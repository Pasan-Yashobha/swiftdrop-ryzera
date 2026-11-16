CREATE TABLE fee_quote (
    id UUID PRIMARY KEY,
    rate_card_id UUID NOT NULL REFERENCES rate_card(id),
    origin_zone_id UUID NOT NULL REFERENCES zone(id),
    destination_zone_id UUID NOT NULL REFERENCES zone(id),
    requested_by VARCHAR(255) NOT NULL,
    weight_kg NUMERIC(5,3) NOT NULL CHECK (weight_kg > 0 AND weight_kg <= 20),
    size_class VARCHAR(10) NOT NULL,
    strategy_used VARCHAR(30) NOT NULL,
    base_amount NUMERIC(14,2) NOT NULL,
    weight_amount NUMERIC(14,2) NOT NULL,
    size_amount NUMERIC(14,2) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at),
    CHECK (total_amount = base_amount + weight_amount + size_amount)
);
