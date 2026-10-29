# Architecture Decisions

Short records of the decisions that shape this repo. Each one: what, why, what I gave up.

## D1 — Two services, not a microservice per entity
**Decision:** `claims-service` (system of record) + `insights-service` (read-side projections).
**Why:** The split follows a real seam: transactional writes vs. analytical reads. Claims, info requests and decisions share one lifecycle and one consistency boundary, so splitting them would mean distributed transactions for no gain.
**Gave up:** Independent scaling of e.g. intake vs. assessment. Revisit if markets need different intake rules or volumes.

## D2 — REST for commands and queries, Kafka for facts
**REST:** anything a user is waiting on — submit claim, pick up, transition, answer info request, read my claims. The caller needs immediate validation and a result (409 on an invalid transition, 400 on a bad payload).
**Kafka:** state changes as facts (`claims.events`, keyed by claimId so per-claim ordering holds). Consumers — today the exposure/workload projection, later notifications, fraud scoring, finance — subscribe without claims-service knowing they exist.
**Not on Kafka:** claim submission. Making submit async would mean the claimant gets "accepted, maybe" with no claim reference and no validation feedback. Wrong trade for a user-facing form.

## D3 — Transactional outbox
State change and event row are written in the same DB transaction; a poller publishes to Kafka. Avoids the dual-write problem (DB commits, Kafka publish fails → projection silently wrong). Delivery is at-least-once, so consumers are idempotent (D4).

## D4 — Event-carried state + version check
Every event carries the claim snapshot (status, reserve, handler, `claimVersion`). Consumers upsert and ignore events with `claimVersion <= stored`. Handles duplicates and replays without a dedupe table, and the projection can be rebuilt by replaying the topic.

## D5 — Event contracts as a shared module
`claims-events` holds plain Java records. Simple and type-safe for a two-service repo. At real scale: Avro/Protobuf + schema registry so producers and consumers can be released independently.

## D6 — One Postgres, database-per-service
One container for local simplicity, but separate databases and no cross-service reads.

## D7 — Explicit command endpoints, not PATCH /status
`POST /claims/{id}/pick-up`, `/approve`, `/reject`… Each business action has its own payload and rules (approve needs an amount, reject needs a reason). A generic status PATCH pushes that logic into a switch statement and makes the API contract vague.

## D8 — Backend tells the UI what's allowed (`allowedActions`)
Claim detail returns the actions the current user may take. The Angular app renders buttons from it, so UI rules can't drift from server rules. The server still re-checks every command.

## D9 — Two views of one claim
Claimants never see the reserve, internal history entries, or staff names. Same endpoint, response shaped by role.

## D10 — 404 for claims you can't see
A claimant probing another claim's id, or a handler outside the team, gets 404 rather than 403, so the existence of a claim is not confirmed.

## D11 — Optimistic locking for concurrent work
`@Version` on the claim. Two handlers picking up the same claim: one wins, the other gets 409 "refresh and try again". Tested with a real race against Postgres.

## D12 — Dev auth only
Seeded users and `Authorization: Bearer <userId>`. Production would be OIDC (Azure AD for staff, a customer IdP for claimants) with role/team from JWT claims; only `DevAuthFilter` changes.

## D13 — Insights is a disposable projection
One row per claim in `claim_projection`, upserted in a single SQL statement guarded by `claim_version`. Duplicates and stale/out-of-order events are no-ops, so the consumer is idempotent without a dedupe table. The table can be dropped and rebuilt by replaying `claims.events` from offset 0. Queries use plain SQL (JdbcClient): aggregations, no domain model needed.

## D14 — Poison messages go to a dead-letter topic
3 retries for transient failures, then `claims.events-dlt`. Unparseable JSON skips the retries. One bad message can't stall a partition and freeze the dashboards.

## D15 — Eventual consistency is shown, not hidden
Dashboards return `asOf` (time of the latest event reflected). Managers get near-real-time exposure without the reporting load ever touching the transactional database.

## D16 — Each service validates tokens itself
Insights doesn't call claims-service to check who you are. In production both validate the same IdP JWT; in dev both read the same seeded IDs. Insights is staff-only: claimants get 403, handlers are pinned to their team.
