CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE rate_card (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    origin_zone_id UUID NOT NULL REFERENCES zone(id),
    destination_zone_id UUID NOT NULL REFERENCES zone(id),
    base_fee NUMERIC(10,2) NOT NULL CHECK (base_fee >= 0),
    per_kg_fee NUMERIC(10,2) NOT NULL CHECK (per_kg_fee >= 0),
    small_surcharge NUMERIC(10,2) NOT NULL CHECK (small_surcharge >= 0),
    medium_surcharge NUMERIC(10,2) NOT NULL CHECK (medium_surcharge >= 0),
    large_surcharge NUMERIC(10,2) NOT NULL CHECK (large_surcharge >= 0),
    valid_from DATE NOT NULL,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    -- The upper date is exclusive, so adjacent cards can switch at midnight.
    CONSTRAINT rate_card_no_overlap EXCLUDE USING gist (
        origin_zone_id WITH =,
        destination_zone_id WITH =,
        daterange(valid_from, valid_to, '[)') WITH &&
    ) WHERE (active)
);
