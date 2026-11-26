# Explore With Me — микросервисная версия с рекомендательной системой

Афиша событий: пользователи публикуют события и находят, с кем пойти. Монолит разделён на микросервисы, добавлена рекомендательная система на Kafka и gRPC.


## Стек

Java 21, Spring Boot 3.3.0, Spring Cloud 2023.0.1 (Eureka, Config Server, Gateway, OpenFeign), Resilience4j, Apache Kafka (Avro), gRPC (Protobuf), PostgreSQL 16.1, Docker Compose, Maven.

## Архитектура

```
                          клиент
                             │
                     ┌───────▼────────┐
                     │ gateway-server │ :8080  (единая точка входа, REST)
                     └───────┬────────┘
         lb://<имя в Eureka> │
 ┌───────────┬───────────────┼──────────────┐
 ▼           ▼               ▼              ▼               
user-     event-          request-       comment-
service   service         service        service
 │         │  │             │               │
ewm_users ewm │          ewm_requests    ewm_comments
              │ gRPC            │ gRPC
              │                 ▼
              │          ┌───────────┐  Avro   ┌───────────────────────────┐
              │          │ collector ├────────▶│ Kafka                     │
              │          └───────────┘         │ stats.user-actions.v1     │
              │ gRPC                           │ stats.events-similarity.v1│
              ▼                                └──┬───────────┬────────────┘
        ┌──────────┐   ewm_analyzer                │           │
        │ analyzer │◀──────────────────────────────┘           │
        └──────────┘◀─────────────────────────┐                ▼
                                              │         ┌────────────┐
                                              └─────────┤ aggregator │
                                                        └────────────┘

Инфраструктура: discovery-server (Eureka) :8761, config-server :8888
```

Каждый сервис владеет своей БД. Связи между сервисами хранятся только как id, внешних ключей между базами нет.

### Сервисы

| Сервис | Модуль | Ответственность | Порт на хосте                             | БД (порт хоста)       |
|---|---|---|-------------------------------------------|-----------------------|
| user-service | `core/user-service` | пользователи | случайный (server.port: 0), через gateway | `ewm_users` (6543)    |
| event-service | `core/event-service` | события, категории, подборки, рейтинг, рекомендации, лайки | случайный, через gateway                  | `ewm` (6542)          |
| request-service | `core/request-service` | заявки на участие | случайный, через gateway                  | `ewm_requests` (6544) |
| comment-service | `core/comment-service` | комментарии к событиям | случайный, через gateway                  | `ewm_comments` (6545) |
| collector | `stat/collector` | принимает действия пользователей по gRPC, пишет в Kafka | 50051 (gRPC)                              | —                     |
| aggregator | `stat/aggregator` | читает действия из Kafka, считает сходство мероприятий, пишет в Kafka | случайный; состояние в памяти, БД нет     | —                     |
| analyzer | `stat/analyzer` | хранит взаимодействия и сходство, отдаёт рекомендации по gRPC | 9100 (gRPC)                               | `ewm_analyzer` (6546) |
| gateway-server | `infra/gateway-server` | маршрутизация внешних запросов, Retry | 8080 (HTTP)                               | —                     |
| discovery-server | `infra/discovery-server` | Eureka, реестр сервисов | 8761                                      | —                     |
| config-server | `infra/config-server` | централизованная конфигурация | 8888       | —                     |
| common-api | `core/common-api` | Feign-интерфейсы, DTO и общий обработчик ошибок | — (библиотека, не запускается)            | —                     |
| stat-client | `stat/stat-client` | gRPC-клиенты `CollectorClient` и `AnalyzerClient` | — (библиотека, не запускается)            | —                     |
| proto-schema | `stat/serialization/proto-schema` | `.proto`-схемы gRPC | — (библиотека, не запускается)            | —                     |
| avro-schema | `stat/serialization/avro-schema` | Avro-схемы (`.avdl`), (де)сериализаторы Kafka | — (библиотека, не запускается)            | —                     |
| kafka | образ `confluentinc/cp-kafka` (в `docker-compose.yml`) | брокер сообщений, топики `stats.user-actions.v1` и `stats.events-similarity.v1` создаёт `kafka-init-topics` | 9092 | —                     |

### Взаимодействие сервисов

- event-service → user-service (инициатор события), request-service (число подтверждённых заявок, участие пользователя)
- request-service → user-service, event-service (проверка события и его правил)
- comment-service → user-service, event-service
- user-service → comment-service (удаление комментариев при удалении пользователя, best-effort)

gRPC:
- event-service → collector (просмотр `ACTION_VIEW`, лайк `ACTION_LIKE`)
- request-service → collector (заявка `ACTION_REGISTER`)
- event-service → analyzer (рейтинг, рекомендации)

Kafka:
- collector → `stats.user-actions.v1` → aggregator, analyzer
- aggregator → `stats.events-similarity.v1` → analyzer

Вызовы идут по имени сервиса из Eureka. gRPC-клиенты используют адрес `discovery:///collector` и `discovery:///analyzer`. 
gRPC-порт регистрируется в metadata Eureka (`gRPC_port`). 
HTTP-вызовы между сервисами выполняются вне транзакций БД.

### Рекомендательная система

Веса действий: просмотр `0.4`, заявка на участие `0.8`, лайк `1.0`. Для пары «пользователь - мероприятие» учитывается максимальный вес.

- **Aggregator** при каждом действии, повышающем максимальный вес, обновляет частные суммы `S_A`, `S_B`, `S_min(A, B)` 
и пересчитывает косинусное сходство `S_min / (√S_A · √S_B)`. 
Идентификаторы пары упорядочены: `eventA < eventB`. 
Состояние хранится в памяти, поэтому при старте топик читается с начала (группа с уникальным id).
- **Analyzer** обновляет таблицы `user_interactions` и `event_similarity` и отвечает на gRPC-запросы:
    - `GetSimilarEvents`: похожие мероприятия, с которыми пользователь ещё не взаимодействовал;
    - `GetRecommendationsForUser`: предсказанная оценка по K ближайшим просмотренным соседям;
    - `GetInteractionsCount`: сумма максимальных весов, это и есть `rating` мероприятия.

### Отказоустойчивость

- Feign-таймауты: connect 1000 мс, read 2000 мс (`infra/config-repo/application.yml`).
- Resilience4j: `@Retry` и `@CircuitBreaker` на вызовах между сервисами.
- Где данные второстепенны, работает fallback: `initiator = null`, `confirmedRequests = 0`, `authorName = null`.
- Где fallback недопустим, клиент получает `503` с телом `{"status": "SERVICE_UNAVAILABLE", ...}`.
- Collector и Analyzer недоступны: действия не записываются, `rating = 0`, список рекомендаций пуст; основные запросы не падают.

## Конфигурация

Все настройки сервисов хранит config-server (native-профиль) в каталоге `infra/config-repo`:

| Файл | Что настраивает |
|---|---|
| `infra/config-repo/application.yml` | общее: Eureka, Feign-таймауты, Resilience4j |
| `infra/config-repo/core/<service>.yml` | порт, БД, gRPC-клиенты конкретного сервиса |
| `infra/config-repo/stat/collector.yml` | Kafka-продюсер, gRPC-порт, топик |
| `infra/config-repo/stat/aggregator.yml` | Kafka-консьюмер и продюсер, топики |
| `infra/config-repo/stat/analyzer.yml` | БД, Kafka-консьюмер, gRPC-порт |
| `infra/config-repo/infra/gateway-server.yml` | маршруты gateway и Retry-фильтры |

Адреса и порты, которые нужно менять снаружи, задаются плейсхолдерами в yml и переменными окружения 
(`EUREKA_URL`, `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `GRPC_SERVER_PORT`, `CONFIG_REPO_PATH`). 
Значения `${VAR:default}` в yml задают значение по умолчанию.

## Запуск

```bash
mvn clean package -DskipTests
docker compose up --build -d
docker compose ps          # все сервисы должны быть healthy
```

Eureka: http://localhost:8761. Внешний API: http://localhost:8080.
Kafka: `localhost:9092` с хоста, `kafka:29092` внутри сети compose. Топики `stats.user-actions.v1` и `stats.events-similarity.v1` создаёт контейнер `kafka-init-topics`.
gRPC с хоста: collector `localhost:50051`, analyzer `localhost:9100`.

## Внешний API

Все публичные запросы идут через gateway (`:8080`). Маршруты:

| Путь | Сервис |
|---|---|
| `/admin/users/**` | user-service |
| `/users/{userId}/requests/**`, `/users/{userId}/events/{eventId}/requests/**` | request-service |
| `/events/{eventId}/comments/**`, `/users/{userId}/comments/**`, `/admin/comments/**` | comment-service |
| остальные `/events/**`, `/users/{userId}/events/**`, `/categories/**`, `/admin/events/**`, `/admin/categories/**`, `/compilations/**`, `/admin/compilations/**` | event-service |

- у события поле `views` заменено на `rating` (`double`): сумма максимальных весов действий пользователей;
- `GET /events/{id}` требует заголовок `X-EWM-USER-ID` и фиксирует просмотр;
- `GET /events` просмотры не фиксирует (`sort=VIEWS` сортирует по рейтингу);
- `GET /events/recommendations` (заголовок `X-EWM-USER-ID`, параметр `maxResults`): рекомендации для пользователя;
- `PUT /events/{eventId}/like` (заголовок `X-EWM-USER-ID`): лайк мероприятия; доступен только пользователю с подтверждённой заявкой, иначе `400`.

Полная спецификация (OpenAPI):
- основной сервис: `ewm-main-service-spec.json`
- исходный групповой проект: `git@github.com:demidov-ko/java-explore-with-me-plus.git`

## Внутренний API

Доступен только между сервисами, через gateway не публикуется. Реализован в виде Feign-клиентов в `common-api`.

### REST (Feign), через gateway не публикуется

| Метод и путь | Сервис | Описание | Клиент |
|---|---|---|---|
| `GET /internal/users/{userId}` | user-service | пользователь по id (404, если нет) | `UserClient` |
| `GET /internal/users?ids=1,2,3` | user-service | пользователи пачкой | `UserClient` |
| `GET /internal/events/{eventId}` | event-service | данные события для проверок | `EventClient` |
| `GET /internal/requests/confirmed?eventIds=1,2` | request-service | `Map<eventId, число подтверждённых заявок>` | `RequestClient` |
| `GET /internal/requests/participation?userId=&eventId=` | request-service | есть ли у пользователя подтверждённая заявка | `RequestClient` |
| `DELETE /internal/comments/by-author/{authorId}` | comment-service | удалить комментарии автора | `CommentClient` |

### gRPC (схемы в `stat/serialization/proto-schema`)

| Сервис | Метод | Вход | Выход |
|---|---|---|---|
| `stats.service.collector.UserActionController` (collector) | `CollectUserAction` | `UserActionProto` (userId, eventId, actionType, timestamp) | `Empty` |
| `stats.service.dashboard.RecommendationsController` (analyzer) | `GetRecommendationsForUser` | `UserPredictionsRequestProto` | поток `RecommendedEventProto` |
| | `GetSimilarEvents` | `SimilarEventsRequestProto` | поток `RecommendedEventProto` |
| | `GetInteractionsCount` | `InteractionsCountRequestProto` | поток `RecommendedEventProto` |

### Kafka (схемы в `stat/serialization/avro-schema/src/main/avro/stats.avdl`)

| Топик | Сообщение | Производитель | Потребители |
|---|---|---|---|
| `stats.user-actions.v1` | `UserActionAvro` | collector | aggregator, analyzer |
| `stats.events-similarity.v1` | `EventSimilarityAvro` | aggregator | analyzer |

## Ошибки

Единый формат (общий `ErrorHandler` в `common-api`):

```json
{ "status": "NOT_FOUND", "reason": "...", "message": "...", "timestamp": "2026-01-01 12:00:00" }
```

Коды: 400, 404, 409, 500 и 503 (зависимый сервис недоступен).

## Структура репозитория

```
core/   user-service, event-service, request-service, comment-service, common-api
stat/   collector, aggregator, analyzer, stat-client, serialization/{proto-schema,avro-schema}
infra/  discovery-server, config-server, gateway-server, config-repo
docker-compose.yml
```
## Тесты

### Проверка рекомендательной системы (tester)

Перед запуском необходимо поднять проект: `docker compose up -d` и дождаться, пока все сервисы
перейдут в `Up (healthy)`. Тестер обращается к сервисам с хоста, поэтому порты должны быть открыты:
Collector `localhost:50051` (gRPC), Analyzer `localhost:9100` (gRPC), Kafka `localhost:9092`,
Eureka `localhost:8761`.

Режимы проверки (`--tester.execution.mode`):

| Режим | Что проверяется |
|---|---|
| `COLLECTION` | только collector (gRPC → топик `stats.user-actions.v1`) |
| `AGGREGATION` | collector + aggregator (топик `stats.events-similarity.v1`) |
| `ANALYZE` | collector + aggregator + analyzer (gRPC-рекомендации) |

Запуск:

    java -jar tester-0.0.1.jar --tester.execution.mode=COLLECTION --tester.execution.output.file-path=./report.txt
    java -jar tester-0.0.1.jar --tester.execution.mode=AGGREGATION --tester.execution.output.file-path=./report.txt
    java -jar tester-0.0.1.jar --tester.execution.mode=ANALYZE --tester.execution.immediate-logging.enabled=true --tester.execution.output.file-path=./report.txt