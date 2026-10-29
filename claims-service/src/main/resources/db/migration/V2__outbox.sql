-- Transactional outbox: written in the same transaction as the claim change,
-- published to Kafka by OutboxPublisher afterwards.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    event_type   VARCHAR(40) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INT         NOT NULL DEFAULT 0
);

-- Poller only ever scans unpublished rows
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
