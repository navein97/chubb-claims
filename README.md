# Chubb APAC Claims Platform

Claims intake, handling and exposure reporting for motor and property claims.

| Component | Port | Role |
|---|---|---|
| `claims-service` | 8081 | System of record. REST API, claim state machine, outbox → Kafka |
| `insights-service` | 8082 | Consumes `claims.events`, builds exposure + workload projections |
| `claims-events` | — | Shared Kafka event contracts |
| Postgres | 5432 | `claims` and `insights` databases |
| Kafka (KRaft) | 9092 | Topic `claims.events` |

## Run
```bash
docker compose up --build
```

See `docs/DECISIONS.md` for architecture decisions and `docs/AI_JOURNAL.md` for the AI working log.
