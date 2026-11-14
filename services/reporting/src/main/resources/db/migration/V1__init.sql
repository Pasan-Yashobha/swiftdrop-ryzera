-- ============================================================
--  V1__init.sql  –  Reporting Service read-model schema
--  Exact schema from Reporting_Service_Build_Brief.md §4
-- ============================================================

-- Idempotency guard: prevents duplicate event processing
CREATE TABLE processed_event (
  event_id     UUID         NOT NULL,
  consumer     VARCHAR(50)  NOT NULL,
  processed_at TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumer)
);

-- One row per parcel, updated with every incoming event
CREATE TABLE parcel_fact (
  parcel_id       UUID         PRIMARY KEY,
  tracking_no     VARCHAR(40),
  merchant_id     UUID,
  courier_id      UUID,
  pickup_zone_id  UUID,
  dropoff_zone_id UUID,
  fee             NUMERIC(12,2),
  current_status  VARCHAR(30)  NOT NULL,
  last_event_at   TIMESTAMP WITH TIME ZONE  NOT NULL,
  ofd_at          TIMESTAMP WITH TIME ZONE,
  delivered_at    TIMESTAMP WITH TIME ZONE
);

-- Company-wide counters per calendar day
CREATE TABLE daily_delivery_stats (
  stat_date        DATE  PRIMARY KEY,
  created_count    INT   NOT NULL DEFAULT 0,
  delivered_count  INT   NOT NULL DEFAULT 0,
  failed_count     INT   NOT NULL DEFAULT 0,
  rescheduled_count INT  NOT NULL DEFAULT 0,
  returned_count   INT   NOT NULL DEFAULT 0,
  cancelled_count  INT   NOT NULL DEFAULT 0
);

-- Per-courier, per-day performance counters
CREATE TABLE courier_daily_stats (
  stat_date            DATE    NOT NULL,
  courier_id           UUID    NOT NULL,
  assigned_count       INT     NOT NULL DEFAULT 0,
  reassigned_away_count INT    NOT NULL DEFAULT 0,
  delivered_count      INT     NOT NULL DEFAULT 0,
  failed_attempts      INT     NOT NULL DEFAULT 0,
  total_minutes        BIGINT  NOT NULL DEFAULT 0,
  timed_count          INT     NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, courier_id)
);

-- Per-zone (drop-off zone), per-day counters
CREATE TABLE zone_daily_stats (
  stat_date       DATE           NOT NULL,
  zone_id         UUID           NOT NULL,
  created_count   INT            NOT NULL DEFAULT 0,
  delivered_count INT            NOT NULL DEFAULT 0,
  failed_count    INT            NOT NULL DEFAULT 0,
  fee_total       NUMERIC(14,2)  NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, zone_id)
);

-- Per-merchant, per-day counters
CREATE TABLE merchant_daily_stats (
  stat_date       DATE  NOT NULL,
  merchant_id     UUID  NOT NULL,
  created_count   INT   NOT NULL DEFAULT 0,
  delivered_count INT   NOT NULL DEFAULT 0,
  failed_count    INT   NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, merchant_id)
);

-- Latest availability snapshot for each courier (not a counter – replaced on each event)
CREATE TABLE courier_state (
  courier_id       UUID        PRIMARY KEY,
  available        BOOLEAN     NOT NULL,
  active_deliveries INT        NOT NULL DEFAULT 0,
  updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
