# Explore With Me — микросервисная версия

Афиша событий: пользователи публикуют события и находят, с кем пойти. Дипломный проект (Яндекс Практикум), этап 2: монолит разделён на микросервисы.

## Стек

Java 21, Spring Boot 3.3.0, Spring Cloud 2023.0.3 (Eureka, Config Server, Gateway, OpenFeign), Resilience4j, PostgreSQL 16.1, Docker Compose, Maven.

## Архитектура

```
                      клиент
                         │
                 ┌───────▼────────┐
                 │ gateway-server │ :8080  (единая точка входа)
                 └───────┬────────┘
     lb://<имя в Eureka> │
 ┌──────────┬────────────┼─────────────┬───────────────┐
 ▼          ▼            ▼             ▼               ▼
user-     event-      request-      comment-        stats-server
service   service     service       service
 │          │            │             │               │
ewm_users  ewm        ewm_requests  ewm_comments    ewm-stats
(6543)    (6542)      (6544)        (6545)          (6541)

Инфраструктура: discovery-server (Eureka) :8761, config-server :8888
```

Каждый сервис владеет своей БД. Связи между сервисами хранятся только как id, внешних ключей между базами нет.

### Сервисы

| Сервис | Модуль | Ответственность | БД (порт хоста) |
|---|---|---|---|
| user-service | `core/user-service` | пользователи, админское управление | `ewm_users` (6543) |
| event-service | `core/event-service` | события, категории, подборки | `ewm` (6542) |
| request-service | `core/request-service` | заявки на участие | `ewm_requests` (6544) |
| comment-service | `core/comment-service` | комментарии к событиям | `ewm_comments` (6545) |
| stats-server | `stat/stat-server` | статистика просмотров (`/hit`, `/stats`) | `ewm-stats` (6541) |
| gateway-server | `infra/gateway-server` | маршрутизация внешних запросов, Retry | — |
| discovery-server | `infra/discovery-server` | Eureka, реестр сервисов | — |
| config-server | `infra/config-server` | централизованная конфигурация | — |
| common-api | `core/common-api` | Feign-интерфейсы, DTO и общий обработчик ошибок | — |

`stat-client` — библиотека для обращения к stats-server (поиск через DiscoveryClient, `stats.service.id: stats-server`, RetryTemplate).

### Взаимодействие сервисов

- event-service → user-service (инициатор события), request-service (число подтверждённых заявок), stats-server (просмотры)
- request-service → user-service, event-service (проверка события и его правил)
- comment-service → user-service, event-service
- user-service → comment-service (удаление комментариев при удалении пользователя, best-effort)

Вызовы идут через OpenFeign по имени сервиса из Eureka. Клиенты лежат в `common-api` и подключаются через `@EnableFeignClients(clients = ...)`. В сервисах вызовы обёрнуты в шлюзы (`UserGateway`, `EventGateway`, `RequestGateway`): они переводят `FeignException.NotFound` в `NotFoundException` и загружают данные пачкой одним запросом, без N+1.

### Отказоустойчивость

- Feign-таймауты: connect 1000 мс, read 2000 мс (`infra/config-repo/application.yml`).
- Resilience4j: `@Retry` и `@CircuitBreaker` на вызовах между сервисами.
- Где данные второстепенны, работает fallback: `initiator = null`, `confirmedRequests = 0`, `authorName = null`.
- Где fallback недопустим, клиент получает `503` с телом `{"status": "SERVICE_UNAVAILABLE", ...}`.
- Если недоступна статистика, события отдаются с нулевыми просмотрами.
- HTTP-вызовы к другим сервисам выполняются вне транзакций БД. Для критичных операций используется `TransactionTemplate`; стандартные методы `JpaRepository` остаются транзакционными по умолчанию.

## Конфигурация

Все настройки сервисов хранит config-server (native-профиль) в каталоге `infra/config-repo`:

| Файл | Что настраивает |
|---|---|
| `infra/config-repo/application.yml` | общее: Eureka, Feign-таймауты, Resilience4j |
| `infra/config-repo/core/<service>.yml` | порт, БД, параметры конкретного сервиса |
| `infra/config-repo/stat/stat-server.yml` | stats-server |
| `infra/config-repo/infra/gateway-server.yml` | маршруты gateway и Retry-фильтры |

В самих сервисах остаётся минимум: имя приложения и `spring.config.import` на config-server. Адреса и пути передаются через переменные окружения (`EUREKA_URL`, `DB_URL`, `CONFIG_REPO_PATH`), задаются в `docker-compose.yml`.

## Запуск

```bash
mvn clean package -DskipTests
docker compose up --build -d
docker compose ps          # все сервисы должны быть healthy
```

Eureka: http://localhost:8761. Внешний API: http://localhost:8080.
Порядок старта обеспечивают healthcheck-и в compose: БД → discovery → config → остальные сервисы → gateway.

## Внешний API

Все публичные запросы идут через gateway (`:8080`). Маршруты:

| Путь | Сервис |
|---|---|
| `/admin/users/**` | user-service |
| `/users/{userId}/requests/**`, `/users/{userId}/events/{eventId}/requests/**` | request-service |
| `/events/{eventId}/comments/**`, `/users/{userId}/comments/**`, `/admin/comments/**` | comment-service |
| остальные `/events/**`, `/users/{userId}/events/**`, `/categories/**`, `/admin/events/**`, `/admin/categories/**`, `/compilations/**`, `/admin/compilations/**` | event-service |
| `/hit`, `/stats` | stats-server |

Полная спецификация (OpenAPI):
- основной сервис: `ewm-main-service-spec.json`
- сервис статистики: `ewm-stats-service-spec.json`
- исходный групповой проект: `git@github.com:demidov-ko/java-explore-with-me-plus.git`

## Внутренний API

Доступен только между сервисами, через gateway не публикуется. Реализован в виде Feign-клиентов в `common-api`.

| Метод и путь | Сервис | Описание | Клиент |
|---|---|---|---|
| `GET /internal/users/{userId}` | user-service | пользователь по id (404, если нет) | `UserClient` |
| `GET /internal/users?ids=1,2,3` | user-service | пользователи пачкой | `UserClient` |
| `GET /internal/events/{eventId}` | event-service | данные события для проверок | `EventClient` |
| `GET /internal/requests/confirmed?eventIds=1,2` | request-service | `Map<eventId, число подтверждённых заявок>` | `RequestClient` |
| `DELETE /internal/comments/by-author/{authorId}` | comment-service | удалить комментарии автора | `CommentClient` |

## Ошибки

Единый формат (общий `ErrorHandler` в `common-api`):

```json
{ "status": "NOT_FOUND", "reason": "...", "message": "...", "timestamp": "2026-01-01 12:00:00" }
```

Коды: 400, 404, 409, 500 и 503 (зависимый сервис недоступен).

## Структура репозитория

```
core/   user-service, event-service, request-service, comment-service, common-api
stat/   stat-server, stat-client, stat-dto
infra/  discovery-server, config-server, gateway-server, config-repo
docker-compose.yml
```
