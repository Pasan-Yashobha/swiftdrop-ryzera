CREATE TABLE promo_code (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(30) NOT NULL UNIQUE,
    discount_type VARCHAR(10) NOT NULL CHECK (discount_type IN ('PERCENT', 'FLAT')),
    discount_value NUMERIC(10,2) NOT NULL CHECK (discount_value > 0),
    min_order_value NUMERIC(16,2) NOT NULL CHECK (min_order_value >= 0),
    max_uses INTEGER CHECK (max_uses > 0),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    valid_from DATE NOT NULL,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CHECK (max_uses IS NULL OR used_count <= max_uses),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

ALTER TABLE fee_quote ADD COLUMN promo_code_id UUID REFERENCES promo_code(id);
ALTER TABLE fee_quote ADD COLUMN promo_code VARCHAR(30);
ALTER TABLE fee_quote ADD COLUMN discount_amount NUMERIC(16,2) NOT NULL DEFAULT 0
    CHECK (discount_amount >= 0);
ALTER TABLE fee_quote DROP CONSTRAINT fee_quote_total_matches_breakdown;
ALTER TABLE fee_quote ADD CONSTRAINT fee_quote_total_matches_breakdown
    CHECK (total_amount = base_amount + weight_amount + size_amount + express_amount - discount_amount
           AND total_amount >= 0);
