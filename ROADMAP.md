# Live Sports Score Service — 300 Days of Code

A 300-day roadmap of real, scoped improvements to live-score-service, executed in
batches by an automated daily task (this project rotates with litekv and semantic-cache,
5 items per run on its day). Each run: implement the items, verify (mvn test / smoke test),
commit, push. No filler commits — if an item can't be completed and verified, it stays
unchecked and gets picked up next run.

Progress is tracked by checking items off below (`- [ ]` → `- [x] (Day N, YYYY-MM-DD)`).

## Domain & API completeness
- [x] PATCH /matches/{id} to correct match status directly (admin override) (Day 1, 2026-08-14)
- [x] DELETE /matches/{id} cascading cleanup of its events and score (Day 1, 2026-08-14)
- [x] Pagination on GET /matches and GET /matches/{id}/events (Day 1, 2026-08-14)
- [x] Filter GET /matches by status (LIVE, SCHEDULED, FINISHED) (Day 1, 2026-08-14)
- [ ] Filter GET /matches/{id}/events by event type
- [ ] Bulk match creation endpoint (POST /matches/batch) for seeding a full matchday
- [ ] Idempotency-Key header support on POST /matches (dedupe accidental double-creates)
- [ ] Optimistic concurrency (@Version) on Match and Score alongside the existing SELECT FOR UPDATE

## Kafka & consumer resilience
- [ ] Dead letter topic for events that fail processing after N retries
- [ ] Consumer retry with exponential backoff before routing to the dead letter topic
- [ ] Manual ack mode with explicit offset commit only after a successful DB write
- [ ] Consumer lag exposed via a custom Actuator/metrics endpoint
- [ ] Graceful consumer shutdown draining in-flight messages before exit
- [ ] Transactional outbox pattern for the producer side, replacing the direct Kafka send in the request thread
- [ ] Partition-aware horizontal scaling smoke test (two consumer instances, same group)

## Persistence
- [ ] Flyway migrations replacing `ddl-auto: update`
- [ ] Indexes on Match(status) and Event(match_id, external_id) for query performance
- [ ] created_at/updated_at audit columns on Match and Score
- [ ] Repository-level pagination (Pageable) wired through the API layer

## Real-time delivery
- [ ] WebSocket/SSE endpoint pushing live score updates instead of polling
- [ ] Redis-backed score cache for hot matches

## Observability
- [ ] Micrometer + Prometheus metrics for event processing throughput/latency
- [ ] Structured JSON logging with correlation/trace IDs
- [ ] Distributed tracing (Micrometer Tracing + OTel) across REST → Kafka → consumer
- [ ] Custom Actuator health indicator for Kafka connectivity
- [ ] /actuator/info populated with build/version metadata

## Testing
- [ ] Contract test for MatchResponse/ScoreResponse/EventResponse DTO stability
- [ ] Load test script (k6 or Gatling) exercising POST /matches/{id}/events concurrently
- [ ] Chaos test: kill the Kafka broker mid-publish, verify the API still responds and the event is retried/DLQ'd
- [ ] Test coverage reporting via JaCoCo

## CI/CD & devex
- [ ] GitHub Actions CI (mvn test on push, using Testcontainers)
- [ ] CI job matrix testing against two Java LTS versions (21 and latest)
- [ ] .dockerignore file to shrink build context
- [ ] docker-compose healthchecks for Postgres and Kafka dependencies
- [ ] Makefile with common dev commands (run, test, docker-up)
- [ ] CONTRIBUTING.md
- [ ] LICENSE file (MIT) if missing
- [ ] CODEOWNERS file

## Security & reliability
- [ ] Rate limiting per client IP on POST /matches/{id}/events
- [ ] Optional API key auth for write endpoints, env-gated
- [ ] Max request payload size limit
- [ ] Graceful shutdown draining in-flight HTTP requests (server.shutdown: graceful)

## Docs
- [ ] API usage examples (curl) for each endpoint in docs/
- [ ] Architecture decision record for the async-write / CQRS-lite design choice
- [ ] OpenAPI/Swagger spec generation (springdoc-openapi)
- [ ] Sequence diagram doc for the event flow, extending the README's ASCII diagram

## Stretch
- [ ] Guidance + smoke test for running multiple app instances behind a load balancer
- [ ] Minimal admin UI (static HTML hitting /matches and /matches/{id}/score)
- [ ] Generalize EventType/domain model beyond football (configurable sport profiles)
- [ ] Final day: 300-day program retrospective + updated architecture doc
