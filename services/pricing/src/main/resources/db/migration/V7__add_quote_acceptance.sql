-- A quote is accepted once, by the Parcel service, when a booking is confirmed.
-- accepted_at is null until then; accepted_parcel_id links the quote to the parcel it priced.
ALTER TABLE fee_quote ADD COLUMN accepted_at TIMESTAMPTZ;
ALTER TABLE fee_quote ADD COLUMN accepted_parcel_id UUID;
ALTER TABLE fee_quote ADD CONSTRAINT fee_quote_parcel_only_when_accepted
    CHECK (accepted_parcel_id IS NULL OR accepted_at IS NOT NULL);
