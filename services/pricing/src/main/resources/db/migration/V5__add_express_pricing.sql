-- Existing cards keep Express disabled until an administrator configures it.
ALTER TABLE rate_card ADD COLUMN express_multiplier NUMERIC(5,2)
    CHECK (express_multiplier >= 1);

ALTER TABLE fee_quote ALTER COLUMN total_amount TYPE NUMERIC(16,2);
ALTER TABLE fee_quote ADD COLUMN express_amount NUMERIC(16,2) NOT NULL DEFAULT 0
    CHECK (express_amount >= 0);
ALTER TABLE fee_quote DROP CONSTRAINT fee_quote_check1;
ALTER TABLE fee_quote ADD CONSTRAINT fee_quote_total_matches_breakdown
    CHECK (total_amount = base_amount + weight_amount + size_amount + express_amount);
