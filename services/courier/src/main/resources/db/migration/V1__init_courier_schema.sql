-- V1__init_courier_schema.sql
-- SwiftDrop Courier Service Draft Schema

-- Courier profiles: maintained by Courier Service
CREATE TABLE courier_profiles (
    courier_id UUID PRIMARY KEY,
    full_name VARCHAR(100),
    phone VARCHAR(32),
    vehicle_type VARCHAR(32) NOT NULL DEFAULT 'BIKE',
    vehicle_plate VARCHAR(32),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    active_deliveries INT NOT NULL DEFAULT 0,
    max_active_deliveries INT NOT NULL DEFAULT 8,
    assigned_zone_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_courier_avail ON courier_profiles (is_available, active_deliveries);

-- Courier assignments: recorded from parcel.assigned events
CREATE TABLE courier_assignments (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL,
    courier_id UUID NOT NULL,
    tracking_no VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(255)
);

CREATE INDEX idx_assignment_parcel ON courier_assignments (parcel_id);
CREATE INDEX idx_assignment_courier ON courier_assignments (courier_id);
CREATE INDEX idx_assignment_status ON courier_assignments (courier_id, status);

-- Delivery proofs: stored recipient name and object storage key (MinIO/S3)
CREATE TABLE delivery_proofs (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES courier_assignments(id),
    recipient_name VARCHAR(100) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_proof_assignment ON delivery_proofs (assignment_id);

-- Processed events: for idempotent event consumption (duplicate message guard)
CREATE TABLE processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Outbox events: transactional outbox for reliable event publishing
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_unsent ON outbox_events (created_at);
