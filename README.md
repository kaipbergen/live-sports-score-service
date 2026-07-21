# Live Sports Score Service

Микросервис live-статистики спортивных матчей: события матча (голы, карточки, старт/финиш)
проходят через Kafka, консьюмер обновляет состояние в PostgreSQL, REST API отдаёт текущий
счёт и ленту событий.

**Стек:** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Kafka, PostgreSQL 16,
Kafka (+ Zookeeper), Docker Compose, JUnit 5, Mockito, Testcontainers.

## Архитектура

```
                ┌──────────────────────────── live-score-service ───────────────────────────┐
                │                                                                           │
  POST /matches/{id}/events                                                                 │
  ──────────────► MatchController ──► MatchService ──► MatchEventProducer ──┐               │
                │                        (валидация,                        │               │
                │                         eventId=UUID)                     ▼               │
                │                                                    Kafka topic            │
                │                                                   "match-events"          │
                │                                                (key = matchId → порядок   │
                │                                                 событий внутри матча)     │
                │                                                           │               │
                │   MatchEventConsumer ◄────────────────────────────────────┘               │
                │        │  @KafkaListener                                                  │
                │        ▼                                                                  │
                │   MatchEventProcessor (@Transactional)                                    │
                │        │  1. идемпотентность: existsByExternalId(eventId) → skip          │
                │        │  2. сохранить Event                                              │
                │        │  3. GOAL → Score++ (SELECT FOR UPDATE)                           │
                │        │     MATCH_STARTED/FINISHED → статус матча                        │
                │        ▼                                                                  │
                │   PostgreSQL (matches, scores, events)                                    │
                │        ▲                                                                  │
  GET /matches/{id}/score, /events                                                          │
  ──────────────► MatchController ──► MatchService ─┘                                       │
                └───────────────────────────────────────────────────────────────────────────┘
```

Запись событий асинхронная (API отвечает `202 Accepted`), чтение — синхронное из БД.
Это упрощённый CQRS: команда идёт через брокер, запросы — напрямую в хранилище.

## Сущности

- **Match** — матч: команды, статус (`SCHEDULED` → `LIVE` → `FINISHED`), время начала.
- **Event** — событие матча: тип (`GOAL`, `YELLOW_CARD`, `RED_CARD`, `SUBSTITUTION`,
  `MATCH_STARTED`, `MATCH_FINISHED`), команда, игрок, минута. `external_id` (UUID из
  Kafka-сообщения) уникален — это ключ идемпотентности.
- **Score** — текущий счёт, `1:1` к матчу.

## API

| Метод | Путь | Описание |
|---|---|---|
| POST | `/matches` | Создать матч (счёт 0:0, статус SCHEDULED) |
| GET | `/matches` | Список матчей |
| GET | `/matches/{id}` | Матч по id |
| POST | `/matches/{id}/events` | Опубликовать событие в Kafka → `202 Accepted` |
| GET | `/matches/{id}/score` | Текущий счёт |
| GET | `/matches/{id}/events` | Лента событий матча |

## Запуск

```bash
docker compose up --build -d      # Postgres + Zookeeper + Kafka + приложение
./scripts/demo.sh                 # демо-сценарий матча через curl
```

Основной `Dockerfile` — честная multi-stage сборка (maven-образ компилирует jar, затем
копируется в jre-образ), не требует Maven на хосте. На медленной сети можно собрать
jar локально и получить готовый образ быстрее:

```bash
mvn -DskipTests package
docker build -f Dockerfile.local -t projectjava-app .
docker compose up -d app
```

Локальная разработка (инфраструктура в Docker, приложение на хосте):

```bash
docker compose up -d postgres zookeeper kafka
# Postgres в контейнере опубликован на хосте как 55432 (5432 уже занят локальным Postgres)
DB_PORT=55432 mvn spring-boot:run
```

## Тесты

```bash
mvn test
```

- `MatchEventProcessorTest` — unit-тесты бизнес-логики консьюмера (Mockito):
  инкремент счёта, идемпотентность, смена статуса.
- `LiveScoreIntegrationTest` — end-to-end через Testcontainers (реальные Postgres и
  Kafka): REST → producer → Kafka → consumer → БД → REST, с Awaitility для
  асинхронной проверки.

## Ключевые решения

- **Kafka key = matchId** — все события одного матча попадают в одну партицию, порядок
  внутри матча гарантирован; разные матчи обрабатываются параллельно (3 партиции).
- **Идемпотентный консьюмер** — Kafka даёт at-least-once; повторная доставка отсекается
  уникальным `external_id` события в БД.
- **`SELECT FOR UPDATE` на Score** — сериализует конкурентные обновления счёта одного
  матча (например, при ребалансе консьюмер-группы).
- **`ErrorHandlingDeserializer`** — «ядовитое» сообщение (битый JSON) не кладёт
  консьюмер в бесконечный цикл ретраев.
- **`202 Accepted` вместо `200`** — API честно говорит, что событие принято в обработку,
  а не применено; состояние станет консистентным асинхронно (eventual consistency).
- **Records для DTO, constructor injection, `open-in-view: false`.**

## Что бы я добавил дальше

- Flyway-миграции вместо `ddl-auto: update`.
- Dead Letter Topic для сообщений, падающих в обработке.
- WebSocket/SSE для push-обновлений счёта клиентам.
- Кэш счёта (Redis) для горячих матчей.
- Метрики (Micrometer + Prometheus) и трейсинг consumer lag.
