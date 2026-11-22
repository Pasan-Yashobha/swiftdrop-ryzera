-- merchant_id is the merchant owner's Identity user id (the JWT subject), the same way
-- receipt.customer_id is the customer's JWT subject. That is what ownership checks compare.
CREATE TABLE invoice (
    id UUID PRIMARY KEY,
    invoice_no VARCHAR(30) NOT NULL UNIQUE,
    merchant_id VARCHAR(255) NOT NULL,
    merchant_name VARCHAR(200) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount NUMERIC(16,2) NOT NULL CHECK (total_amount >= 0),
    currency VARCHAR(3) NOT NULL,
    line_count INTEGER NOT NULL CHECK (line_count > 0),
    snapshot_hash CHAR(64) NOT NULL,
    pdf_content BYTEA NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT invoice_period_valid CHECK (period_end >= period_start),
    -- One invoice per merchant and billing period; a retry finds the existing row.
    CONSTRAINT invoice_merchant_period_unique UNIQUE (merchant_id, period_start, period_end)
);
CREATE INDEX invoice_merchant_generated_idx ON invoice(merchant_id, generated_at DESC, id);

CREATE TABLE invoice_line (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoice(id),
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    -- UNIQUE: a parcel can be billed on one invoice only.
    parcel_id UUID NOT NULL UNIQUE,
    tracking_number VARCHAR(60) NOT NULL,
    description VARCHAR(120) NOT NULL,
    amount NUMERIC(16,2) NOT NULL CHECK (amount >= 0),
    CONSTRAINT invoice_line_order_unique UNIQUE (invoice_id, line_no)
);
