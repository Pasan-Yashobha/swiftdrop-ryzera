-- Consumer-local lock and progress metadata. Existing assignment history is retained.
CREATE TABLE assignment_event_cursors (
    parcel_id UUID PRIMARY KEY,
    assignment_id UUID REFERENCES courier_assignments(id),
    last_occurred_at TIMESTAMP WITH TIME ZONE
);
