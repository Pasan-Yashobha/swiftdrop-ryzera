-- V1__init_dispatch_schema.sql
-- SwiftDrop Dispatch Service Schema for PostgreSQL 16

CREATE TABLE assignment (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL,
    courier_id UUID NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255)
);

-- Protect against double assignment: only one ACTIVE assignment per parcel allowed
CREATE UNIQUE INDEX ux_assignment_active_parcel ON assignment (parcel_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_assignment_courier ON assignment (courier_id);
CREATE INDEX idx_assignment_assigned_at ON assignment (assigned_at);

CREATE TABLE courier_workload (
    courier_id UUID PRIMARY KEY,
    zone_id UUID,
    active_deliveries INT NOT NULL DEFAULT 0,
    max_active INT NOT NULL DEFAULT 8,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    last_assigned_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_courier_workload_avail ON courier_workload (available, active_deliveries);

CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;
