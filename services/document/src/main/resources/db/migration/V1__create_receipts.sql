CREATE TABLE receipt (
    id UUID PRIMARY KEY,
    receipt_no VARCHAR(45) NOT NULL UNIQUE,
    parcel_id UUID NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    tracking_number VARCHAR(60) NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL,
    amount_total NUMERIC(16,2) NOT NULL CHECK (amount_total >= 0),
    currency VARCHAR(3) NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    pdf_content BYTEA NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX receipt_customer_generated_idx ON receipt(customer_id, generated_at DESC, id);
