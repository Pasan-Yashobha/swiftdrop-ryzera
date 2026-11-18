-- Parcel Service schema (database: parcel_db, owner: parcel_svc)
-- Compatible with PostgreSQL 16 and H2 (PostgreSQL mode)

create table parcels (
    id                    uuid                     primary key,
    tracking_number       varchar(40)              not null,
    customer_id           varchar(64),
    merchant_id           uuid,
    recipient_name        varchar(120)             not null,
    recipient_phone       varchar(32)              not null,
    recipient_email       varchar(254),
    pickup_address_line   varchar(300)             not null,
    pickup_city           varchar(100)             not null,
    pickup_postal_code    varchar(16),
    dropoff_address_line  varchar(300)             not null,
    dropoff_city          varchar(100)             not null,
    dropoff_postal_code   varchar(16),
    weight_kg             numeric(5,2)             not null,
    size                  varchar(16)              not null,
    description           varchar(255),
    delivery_type         varchar(16)              not null,
    notes                 varchar(500),
    status                varchar(32)              not null,
    quote_id              uuid,
    base_amount           numeric(10,2)            not null,
    weight_amount         numeric(10,2)            not null,
    size_amount           numeric(10,2)            not null,
    express_amount        numeric(10,2)            not null default 0.00,
    discount_amount       numeric(10,2)            not null default 0.00,
    total_fee             numeric(10,2)            not null,
    currency              varchar(3)               not null default 'LKR',
    courier_id            varchar(64),
    proof_recipient_name  varchar(120),
    proof_photo_key       varchar(255),
    failure_reason        varchar(500),
    delivered_at          timestamp with time zone,
    created_at            timestamp with time zone not null,
    updated_at            timestamp with time zone not null,
    version               bigint                   not null default 0,
    constraint uq_parcels_tracking unique (tracking_number),
    constraint ck_parcels_status check (status in ('CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'RESCHEDULED', 'RETURNED', 'CANCELLED')),
    constraint ck_parcels_size check (size in ('SMALL', 'MEDIUM', 'LARGE')),
    constraint ck_parcels_delivery_type check (delivery_type in ('STANDARD', 'EXPRESS')),
    constraint ck_parcels_weight check (weight_kg > 0)
);

create index ix_parcels_tracking_number on parcels (tracking_number);
create index ix_parcels_customer_id on parcels (customer_id);
create index ix_parcels_merchant_id on parcels (merchant_id);
create index ix_parcels_status on parcels (status);
create index ix_parcels_courier_id on parcels (courier_id);
create index ix_parcels_created_at on parcels (created_at);

create table parcel_status_history (
    id                    uuid                     primary key,
    parcel_id             uuid                     not null references parcels (id) on delete cascade,
    sequence_number       integer                  not null,
    from_status           varchar(32),
    to_status             varchar(32)              not null,
    changed_by            varchar(64)              not null,
    actor_role            varchar(32)              not null,
    notes                 varchar(500),
    occurred_at           timestamp with time zone not null,
    constraint uq_parcel_history_seq unique (parcel_id, sequence_number)
);

create index ix_parcel_history_parcel on parcel_status_history (parcel_id);

create table idempotency_records (
    idempotency_key       varchar(255)             primary key,
    request_hash          varchar(64)              not null,
    response_status       integer,
    response_body         text,
    status                varchar(16)              not null,
    created_at            timestamp with time zone not null,
    expires_at            timestamp with time zone not null,
    constraint ck_idempotency_status check (status in ('IN_PROGRESS', 'COMPLETED'))
);

create table outbox_events (
    id                    uuid                     primary key,
    event_id              uuid                     not null,
    event_type            varchar(100)             not null,
    aggregate_type        varchar(50)              not null,
    aggregate_id          varchar(100)             not null,
    payload               text                     not null,
    created_at            timestamp with time zone not null,
    published_at          timestamp with time zone,
    attempts              integer                  not null default 0,
    last_error            varchar(500)
);

create index ix_outbox_events_published on outbox_events (published_at, created_at);
