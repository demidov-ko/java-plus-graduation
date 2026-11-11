# Explore with me  - микросервисная версия

Приложение для поиска и организации событий. Состоит из основного сервиса
(события, пользователи, категории, запросы на участие) и сервиса статистики
(сбор просмотров и выдача статистики), объединённых через API Gateway.

## Архитектура

- **config-server** — централизованное хранение конфигураций всех сервисов
  (папка `infra/config-repo`).
- **discovery-server (Eureka)** — реестр сервисов, порт `8761`.
- **gateway-server** — единая точка входа, порт `8080`. Маршруты на основной
  и статистический сервисы заданы явно (`lb://`), для GET-маршрутов настроен
  Retry при 502/503/504.
- **main-service** — основной сервис (порт динамический).
- **stat-server** — сервис статистики: принимает `POST /hit`, отдаёт
  `GET /stats` (порт динамический).
- **PostgreSQL** — две базы: `ewm` (порт 6542) и `ewm-stats` (порт 6541).

Все сервисы регистрируются в Eureka и берут конфигурацию из Config Server
через discovery (`spring.config.import: "configserver:"` +
`spring.cloud.config.discovery.enabled=true`).

Все сервисы регистрируются в Eureka и берут конфигурацию из Config Server
через discovery

## Запуск

1. Поднять PostgreSQL
2. Запустить сервисы в порядке:
   `discovery-server` → `config-server` → `stat-server` → `main-service` → `gateway-server`.
3. Приложение доступно через gateway: `http://localhost:8080`.

`http://localhost:8080/actuator/gateway/routes` - диагностический эндпоинт Actuator'а самого Gateway. 
Он показывает список всех реально загруженных маршрутов шлюза: id, предикаты (какие пути и методы ловит), 
фильтры (Retry) и целевой URI (lb://...).