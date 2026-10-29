CREATE TABLE claim (
    id                    UUID PRIMARY KEY,
    reference             VARCHAR(40)    NOT NULL UNIQUE,
    version               BIGINT         NOT NULL,
    claimant_id           UUID           NOT NULL,
    claimant_name         VARCHAR(200)   NOT NULL,
    policy_number         VARCHAR(60)    NOT NULL,
    market                VARCHAR(2)     NOT NULL,
    type                  VARCHAR(20)    NOT NULL,
    incident_date         DATE           NOT NULL,
    description           VARCHAR(4000)  NOT NULL,
    estimated_loss        NUMERIC(19, 2) NOT NULL,
    status                VARCHAR(30)    NOT NULL,
    team                  VARCHAR(40)    NOT NULL,
    assigned_handler_id   UUID,
    assigned_handler_name VARCHAR(200),
    reserve_amount        NUMERIC(19, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    settled_amount        NUMERIC(19, 2),
    decision_reason       VARCHAR(2000),
    submitted_at          TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL
);

-- Staff queue: unassigned/open claims per team, oldest first
CREATE INDEX idx_claim_team_status ON claim (team, status, submitted_at);
CREATE INDEX idx_claim_handler ON claim (assigned_handler_id, status);
CREATE INDEX idx_claim_claimant ON claim (claimant_id, submitted_at DESC);

CREATE TABLE info_request (
    id           UUID PRIMARY KEY,
    claim_id     UUID          NOT NULL REFERENCES claim (id),
    question     VARCHAR(2000) NOT NULL,
    response     VARCHAR(4000),
    requested_by UUID          NOT NULL,
    requested_at TIMESTAMPTZ   NOT NULL,
    responded_at TIMESTAMPTZ
);
CREATE INDEX idx_info_request_claim ON info_request (claim_id);

CREATE TABLE claim_history (
    id                  UUID PRIMARY KEY,
    claim_id            UUID          NOT NULL REFERENCES claim (id),
    occurred_at         TIMESTAMPTZ   NOT NULL,
    actor_id            UUID          NOT NULL,
    actor_name          VARCHAR(200)  NOT NULL,
    from_status         VARCHAR(30),
    to_status           VARCHAR(30)   NOT NULL,
    note                VARCHAR(2000) NOT NULL,
    visible_to_claimant BOOLEAN       NOT NULL
);
CREATE INDEX idx_claim_history_claim ON claim_history (claim_id, occurred_at);
