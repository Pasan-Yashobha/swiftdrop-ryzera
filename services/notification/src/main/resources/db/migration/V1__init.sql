-- Notification Service schema (notification_db).

-- Message templates, one per event type and channel. Editable at runtime from the admin console, so the wording can
-- change without a redeploy. Placeholders use {{name}} and are filled from the event data and the recipient.
CREATE TABLE notification_templates (
    id          UUID          NOT NULL PRIMARY KEY,
    event_type  VARCHAR(100)  NOT NULL,
    channel     VARCHAR(10)   NOT NULL,
    audience    VARCHAR(20)   NOT NULL,
    subject     VARCHAR(200),
    body        VARCHAR(2000) NOT NULL,
    active      BOOLEAN       NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_event_channel UNIQUE (event_type, channel),
    CONSTRAINT ck_template_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_template_audience CHECK (audience IN ('CUSTOMER', 'MERCHANT'))
);

-- Idempotent consumer: an event id is processed at most once.
CREATE TABLE processed_events (
    event_id     UUID         NOT NULL PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One row per event and channel: the notification log shown to admins.
-- Contact details are never stored: recipient_ref is a user or merchant id and recipient_masked is for display only.
-- envelope holds the original event (ids only) so a failed channel can be re-rendered and retried.
CREATE TABLE notifications (
    id               UUID          NOT NULL PRIMARY KEY,
    event_id         UUID          NOT NULL,
    event_type       VARCHAR(100)  NOT NULL,
    correlation_id   VARCHAR(64),
    channel          VARCHAR(10)   NOT NULL,
    audience         VARCHAR(20)   NOT NULL,
    template_id      UUID          NOT NULL,
    template_key     VARCHAR(120)  NOT NULL,
    recipient_ref    VARCHAR(64),
    recipient_masked VARCHAR(64),
    subject          VARCHAR(200),
    envelope         VARCHAR(8000) NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    attempts         INTEGER       NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    next_attempt_at  TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at          TIMESTAMP WITH TIME ZONE,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_notification_event_channel UNIQUE (event_id, channel),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'RETRYING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_notifications_created_at ON notifications (created_at);
CREATE INDEX ix_notifications_status_next ON notifications (status, next_attempt_at);

-- What the development SMS provider (mock) received. Only the masked number is kept.
CREATE TABLE mock_sms_messages (
    id          UUID          NOT NULL PRIMARY KEY,
    to_masked   VARCHAR(32)   NOT NULL,
    message     VARCHAR(1000) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_mock_sms_received_at ON mock_sms_messages (received_at);

-- Default templates.
INSERT INTO notification_templates (id, event_type, channel, audience, subject, body, active, updated_at) VALUES
('0a3c1f6e-0001-4000-8000-000000000001', 'parcel.created', 'EMAIL', 'CUSTOMER',
 'Your SwiftDrop parcel {{trackingNo}} is booked',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'Your parcel {{trackingNo}} has been booked with SwiftDrop. We will let you know when it is on the way.' || CHR(10) || CHR(10) || 'Track it here: {{trackingLink}}' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0002-4000-8000-000000000002', 'parcel.status.picked_up', 'EMAIL', 'CUSTOMER',
 'Parcel {{trackingNo}} has been picked up',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'Our courier has picked up your parcel {{trackingNo}}.' || CHR(10) || CHR(10) || 'Track it here: {{trackingLink}}' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0003-4000-8000-000000000003', 'parcel.status.out_for_delivery', 'EMAIL', 'CUSTOMER',
 'Parcel {{trackingNo}} is out for delivery',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'Good news: your parcel {{trackingNo}} is out for delivery and should reach you today.' || CHR(10) || CHR(10) || 'Track it here: {{trackingLink}}' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0004-4000-8000-000000000004', 'parcel.status.out_for_delivery', 'SMS', 'CUSTOMER', NULL,
 'SwiftDrop: Your parcel {{trackingNo}} is out for delivery. Track it here: {{trackingLink}}',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0005-4000-8000-000000000005', 'parcel.status.delivered', 'EMAIL', 'CUSTOMER',
 'Parcel {{trackingNo}} was delivered',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'Your parcel {{trackingNo}} has been delivered. Thank you for using SwiftDrop.' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0006-4000-8000-000000000006', 'parcel.status.delivered', 'SMS', 'CUSTOMER', NULL,
 'SwiftDrop: Your parcel {{trackingNo}} has been delivered. Thank you!',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0007-4000-8000-000000000007', 'parcel.status.failed', 'EMAIL', 'CUSTOMER',
 'We could not deliver parcel {{trackingNo}}',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'We tried to deliver your parcel {{trackingNo}} but could not complete the delivery ({{failureReason}}). We will contact you to arrange another attempt.' || CHR(10) || CHR(10) || 'Track it here: {{trackingLink}}' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0008-4000-8000-000000000008', 'parcel.status.failed', 'SMS', 'CUSTOMER', NULL,
 'SwiftDrop: We could not deliver parcel {{trackingNo}} today. Details: {{trackingLink}}',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0009-4000-8000-000000000009', 'parcel.cancelled', 'EMAIL', 'CUSTOMER',
 'Parcel {{trackingNo}} was cancelled',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'The delivery of parcel {{trackingNo}} has been cancelled.' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0010-4000-8000-000000000010', 'receipt.generated', 'EMAIL', 'CUSTOMER',
 'Your SwiftDrop receipt for {{trackingNo}}',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'Thank you for using SwiftDrop. Your receipt for parcel {{trackingNo}} ({{currency}} {{totalAmount}}) is attached as a PDF.' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0011-4000-8000-000000000011', 'merchant.approved', 'EMAIL', 'MERCHANT',
 'Your SwiftDrop merchant account is approved',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || '{{businessName}} has been approved. You can now create API keys and start booking deliveries in the Merchant Portal.' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0012-4000-8000-000000000012', 'merchant.rejected', 'EMAIL', 'MERCHANT',
 'Your SwiftDrop merchant application',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'We could not approve {{businessName}} yet. Reason: {{reason}}' || CHR(10) || CHR(10) || 'You can update your details and resubmit in the Merchant Portal.' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP),
('0a3c1f6e-0013-4000-8000-000000000013', 'webhook.delivery.exhausted', 'EMAIL', 'MERCHANT',
 'Action needed: your SwiftDrop webhook endpoint is not responding',
 'Hi {{recipientName}},' || CHR(10) || CHR(10) || 'We could not deliver the {{eventType}} webhook (event {{eventId}}) to your endpoint after {{attempts}} attempts. The last HTTP status was {{lastHttpStatus}}.' || CHR(10) || CHR(10) || 'Please check your endpoint. Once it is fixed, our team can replay the failed deliveries.' || CHR(10) || CHR(10) || 'SwiftDrop',
 TRUE, CURRENT_TIMESTAMP);
