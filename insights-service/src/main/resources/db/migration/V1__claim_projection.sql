-- Read model built from claims.events. One row per claim, latest known state.
-- Disposable: drop it and replay the topic from offset 0 to rebuild.
CREATE TABLE claim_projection (
    claim_id       UUID PRIMARY KEY,
    claim_ref      VARCHAR(40)    NOT NULL,
    market         VARCHAR(2)     NOT NULL,
    claim_type     VARCHAR(20)    NOT NULL,
    team           VARCHAR(40)    NOT NULL,
    status         VARCHAR(30)    NOT NULL,
    handler_id     UUID,
    handler_name   VARCHAR(200),
    reserve_amount NUMERIC(19, 2) NOT NULL,
    settled_amount NUMERIC(19, 2),
    currency       VARCHAR(3)     NOT NULL,
    submitted_at   TIMESTAMPTZ    NOT NULL,
    closed_at      TIMESTAMPTZ,
    claim_version  BIGINT         NOT NULL,
    last_event_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_proj_open ON claim_projection (market, currency, claim_type) WHERE closed_at IS NULL;
CREATE INDEX idx_proj_team ON claim_projection (team, status);
CREATE INDEX idx_proj_handler ON claim_projection (handler_id, status);
