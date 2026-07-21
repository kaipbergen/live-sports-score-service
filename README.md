# Live Sports Score Service

A small microservice for live match scores. Match events (goals, cards, kickoff/full-time)
flow through Kafka, a consumer applies them to the state in PostgreSQL, and a REST API
exposes the current score and event feed.

I built this as a hands-on project to get real practice with Kafka + Spring Boot + Postgres
beyond tutorial-level examples — closer to how you'd actually structure an event-driven
service.

**Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Kafka, PostgreSQL 16,
Kafka (+ Zookeeper), Docker Compose, JUnit 5, Mockito, Testcontainers.

## Architecture

```
                ┌──────────────────────────── live-score-service ───────────────────────────┐
                │                                                                           │
  POST /matches/{id}/events                                                                 │
  ──────────────► MatchController ──► MatchService ──► MatchEventProducer ──┐               │
                │                        (validation,                       │               │
                │                         eventId=UUID)                     ▼               │
                │                                                    Kafka topic            │
                │                                                   "match-events"          │
                │                                                (key = matchId → order     │
                │                                                 preserved per match)       │
                │                                                           │               │
                │   MatchEventConsumer ◄────────────────────────────────────┘               │
                │        │  @KafkaListener                                                  │
                │        ▼                                                                  │
                │   MatchEventProcessor (@Transactional)                                    │
                │        │  1. idempotency check: existsByExternalId(eventId) → skip         │
                │        │  2. persist Event                                                 │
                │        │  3. GOAL → Score++ (SELECT FOR UPDATE)                            │
                │        │     MATCH_STARTED/FINISHED → match status update                  │
                │        ▼                                                                  │
                │   PostgreSQL (matches, scores, events)                                    │
                │        ▲                                                                  │
  GET /matches/{id}/score, /events                                                          │
  ──────────────► MatchController ──► MatchService ─┘                                       │
                └───────────────────────────────────────────────────────────────────────────┘
```

Writes are async (the API answers `202 Accepted`), reads go straight to the DB. It's a
lightweight CQRS split: commands travel through the broker, queries hit the store directly.

## Domain model

- **Match** — the match itself: teams, status (`SCHEDULED` → `LIVE` → `FINISHED`), start time.
- **Event** — a match event: type (`GOAL`, `YELLOW_CARD`, `RED_CARD`, `SUBSTITUTION`,
  `MATCH_STARTED`, `MATCH_FINISHED`), team, player, minute. `external_id` (the UUID from the
  Kafka message) is unique — that's what idempotency is keyed on.
- **Score** — the running score, one row per match.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/matches` | Create a match (score 0:0, status SCHEDULED) |
| GET | `/matches` | List matches |
| GET | `/matches/{id}` | Get a match by id |
| POST | `/matches/{id}/events` | Publish an event to Kafka → `202 Accepted` |
| GET | `/matches/{id}/score` | Current score |
| GET | `/matches/{id}/events` | Match event feed |

## Running it

```bash
docker compose up --build -d      # Postgres + Zookeeper + Kafka + app
./scripts/demo.sh                 # runs a full match scenario through curl
```

The main `Dockerfile` is a proper multi-stage build (a maven image compiles the jar, then
it's copied into a jre image) — no need for Maven on the host. If your network is slow and
pulling `maven:3.9-eclipse-temurin-21` is painful, you can build the jar locally and get a
runnable image faster:

```bash
mvn -DskipTests package
docker build -f Dockerfile.local -t projectjava-app .
docker compose up -d app
```

Local dev (infra in Docker, app on the host):

```bash
docker compose up -d postgres zookeeper kafka
# the Postgres container is published on the host as 55432 (5432 was already
# taken by a local Postgres install on my machine)
DB_PORT=55432 mvn spring-boot:run
```

## Tests

```bash
mvn test
```

- `MatchEventProcessorTest` — unit tests for the consumer's business logic (Mockito):
  score increments, idempotency, status transitions.
- `LiveScoreIntegrationTest` — end-to-end via Testcontainers (real Postgres and Kafka):
  REST → producer → Kafka → consumer → DB → REST, using Awaitility to assert on the
  async result.

## Design decisions worth calling out

- **Kafka key = matchId** — all events for a given match land on the same partition, so
  ordering within a match is guaranteed; different matches are processed in parallel
  (3 partitions).
- **Idempotent consumer** — Kafka only gives you at-least-once delivery, so redelivered
  messages are filtered out by the event's unique `external_id` in the DB.
- **`SELECT FOR UPDATE` on Score** — serializes concurrent score updates for the same match
  (e.g. during a consumer group rebalance).
- **`ErrorHandlingDeserializer`** — a poison message (malformed JSON) doesn't send the
  consumer into an infinite retry loop.
- **`202 Accepted` instead of `200`** — the API is honest that the event was *accepted*,
  not applied yet; the state becomes consistent asynchronously (eventual consistency).
- Records for DTOs, constructor injection everywhere, `open-in-view: false`.

## What I'd add next

- Flyway migrations instead of `ddl-auto: update`.
- A dead letter topic for messages that fail processing.
- WebSocket/SSE so clients get score updates pushed instead of polling.
- A score cache (Redis) for hot matches.
- Metrics (Micrometer + Prometheus) and consumer lag tracing.
