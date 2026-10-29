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

<!-- Template for new entries
### N. <topic>
- **Asked:**
- **AI said:**
- **Outcome:** accepted / challenged / overrode
- **Why:**
-->
