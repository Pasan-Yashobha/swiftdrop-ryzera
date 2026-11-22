-- Append-only audit trail. One row per audited event.
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,   -- insertion order, used by the hash chain
    event_id       UUID         NOT NULL UNIQUE,                 -- makes the consumer idempotent
    occurred_at    TIMESTAMPTZ  NOT NULL,
    recorded_at    TIMESTAMPTZ  NOT NULL,
    actor_id       VARCHAR(100),
    actor_role     VARCHAR(30),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(50),
    entity_id      VARCHAR(100),
    ip_address     VARCHAR(64),
    correlation_id VARCHAR(100),
    producer       VARCHAR(100),
    details        JSONB,
    prev_hash      VARCHAR(64)  NOT NULL,
    row_hash       VARCHAR(64)  NOT NULL
);

CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor    ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_log_entity   ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_action   ON audit_log (action);
