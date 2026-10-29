# AI Working Journal

Running log of how I used AI on this build: what I asked, what I accepted, what I challenged or overrode, and why.
Format per entry: **Asked / AI said / Outcome (accepted · challenged · overrode) / Why.**

---

### 1. Decomposing the brief
- **Asked:** Break down the brief: stack, service boundaries, what to build first.
- **AI said:** Two services (claims system of record + Kafka-fed insights projection), REST for commands, Kafka for state-change facts, outbox for consistency, explicit cut list.
- **Outcome:** Accepted.
- **Why:** Six services in a 3–5h window is unbuildable and undefendable. The read/write seam is the one split I can justify.

### 2. .NET vs Spring Boot
- **Asked:** Which backend to use.
- **AI said:** Spring Boot — Kafka is first-class in Java (Spring Kafka retries/DLT), Testcontainers + springdoc are well trodden.
- **Outcome:** Accepted. Java 21 + Spring Boot 3.5.
- **Why:** _(add your own reason — e.g. which one you're more fluent in)_

### 3. "Just build the whole thing"
- **Asked:** Build the entire app in one go.
- **AI said:** Pushed back — one-shot dump makes the commit history and this journal meaningless; proposed staged build with my review between stages.
- **Outcome:** Accepted staged approach.
- **Why:** I sign off every line; I can only do that if I review in chunks.

---

### 4. Bugs the tests caught in AI-generated code (stage 3)
- **Lost domain events:** service drained pending events from the instance returned by `save()`. With a primitive `long @Version`, Spring Data treats the entity as existing and calls `merge()`, which returns a *copy* without the transient event list. Fixed: wrapper `Long` version (so `persist()` is used) and drain events from the original instance.
- **LazyInitializationException risk:** controllers map DTOs after the transaction closes (open-in-view off). Fixed by initialising history/info requests inside the service transaction.
- **Testcontainers 1.21.3 vs Docker Engine 29:** API version too old. Pinned 1.21.4.
- **Outcome:** Overrode the first versions. **Why:** the integration test against real Postgres is what exposed these — unit tests alone would have passed.

---

### 5. Outbox + Kafka (stage 4)
- **Kafka Testcontainer wouldn't start** in the build sandbox (Postgres container was fine). Switched tests to spring-kafka-test's in-process KRaft broker: faster, no Docker dependency for Kafka. `docker compose` still runs real Kafka.
- **Events vanished in the full test run but passed alone.** Cause: two Spring test contexts (one class had `@AutoConfigureMockMvc`, one didn't) → two outbox pollers + two embedded brokers sharing one Postgres. The first context's poller published the second test's events to *its* broker. Fixed by giving every IT one shared configuration.
- **Outcome:** Overrode. **Why:** same failure mode as running two service instances against one outbox — which is exactly why the poller uses `FOR UPDATE SKIP LOCKED`. Good talking point for "what happens when you scale claims-service horizontally?"

---

### 6. Insights service (stage 5)
- **`asOf` crashed on an empty dashboard:** `max()` over an empty table returns one NULL row, and `query(Timestamp.class).single()` rejects null. Caught because the test checks the empty state first. Fixed by mapping the row manually.
- **Dead-letter topic name:** first version created `claims.events.DLT`; Spring Kafka 3.x publishes to `claims.events-dlt`, so poison messages went to an auto-created topic. Caught by the DLT assertion.
- **Docker image build** can't download Maven deps inside the AI's sandbox (proxy), so the cross-service smoke test ran the jars against compose Postgres/Kafka. To verify on my machine: `docker compose up --build`.
- **Outcome:** Overrode. **Why:** both bugs were silent in normal use — only edge-case tests exposed them.

---

### 7. Angular app (stages 6–7)
- **Angular version:** CLI scaffolded Angular 22 (newer than the AI's training data). Kept it, wrote conservative code (standalone components, signals, `@if/@for`), and relied on the compiler and a headless browser run to catch anything version-specific. Zoneless by default, so all state is signals.
- **Contract from the backend:** generated TS types from both OpenAPI specs instead of hand-writing interfaces. Downside spotted: springdoc marks every response field optional, so templates need null handling. Two compile errors from exactly that were fixed.
- **Bug caught on review:** after a failed action (e.g. 409), the claim screen re-fetched and the refresh cleared the error message before the user could read it. Fixed with a `keepError` flag on reload.
- **Copy:** rejected status names like "UNDER_ASSESSMENT" for claimants; used plain language ("Being assessed", "We need more information") and kept internal names for staff.
- **Verified** every screen with Playwright: full claimant and staff flows, a lost pick-up race, manager dashboard, 390px mobile, zero console errors.
- **Outcome:** Mostly accepted, with the fixes above. **Why:** the UI is secondary in the brief, so effort went to flows that show the backend decisions (allowedActions, 409 handling, freshness).

### 8. Packaging (stage 8)
- **Demo seeder** creates claims through the real application service, not SQL inserts, so events flow through the outbox and the dashboards fill up the same way production would.
- **nginx** serves the Angular build and reverse-proxies both APIs on one origin: no CORS config, same paths as the dev proxy.

---

<!-- Template for new entries
### N. <topic>
- **Asked:**
- **AI said:**
- **Outcome:** accepted / challenged / overrode
- **Why:**
-->
