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

<!-- Template for new entries
### N. <topic>
- **Asked:**
- **AI said:**
- **Outcome:** accepted / challenged / overrode
- **Why:**
-->
