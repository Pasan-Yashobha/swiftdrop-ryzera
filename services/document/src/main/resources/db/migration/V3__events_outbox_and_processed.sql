-- Events this service has already handled, so a redelivered message is acknowledged and skipped.
CREATE TABLE processed_event (
    event_id UUID NOT NULL,
    consumer VARCHAR(60) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer)
);

-- Transactional outbox: written in the same transaction as the receipt or invoice, published by the relay.
-- payload holds the whole event envelope as JSON text; events carry identifiers only, no personal data.
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(300)
);
-- The relay only ever looks at rows that are not published yet.
CREATE INDEX outbox_event_pending_idx ON outbox_event(created_at, id) WHERE published_at IS NULL;

-- The merchant's id in the Merchant service. Webhooks look a merchant up by this id (merchant_id above is the
-- owner's Identity user id, which is what access checks compare). Optional: without it the event cannot be
-- routed to a webhook.
ALTER TABLE invoice ADD COLUMN merchant_service_id UUID;
