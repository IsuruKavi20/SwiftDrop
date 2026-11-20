-- Notification & Webhook module: records notification dispatches, templates, and merchant webhooks.

CREATE TABLE notification_template (
    template_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type        VARCHAR(50)  NOT NULL,
    channel           VARCHAR(20)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    subject_template  VARCHAR(255),
    body_template     TEXT         NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_template_event_channel UNIQUE (event_type, channel)
);

CREATE TABLE notification_log (
    notification_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id       BIGINT REFERENCES delivery (delivery_id) ON DELETE SET NULL,
    recipient_contact VARCHAR(150) NOT NULL,
    recipient_role    VARCHAR(30)  NOT NULL CHECK (recipient_role IN ('CUSTOMER', 'COURIER', 'DISPATCHER')),
    channel           VARCHAR(20)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    event_type        VARCHAR(50)  NOT NULL,
    subject           VARCHAR(255),
    content           TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    error_message     TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at           TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notif_log_delivery ON notification_log (delivery_id);
CREATE INDEX idx_notif_log_status ON notification_log (status);

CREATE TABLE merchant_webhook_config (
    webhook_config_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id        BIGINT       NOT NULL REFERENCES merchant (merchant_id) ON DELETE CASCADE,
    endpoint_url       VARCHAR(500) NOT NULL,
    secret_key         VARCHAR(128) NOT NULL,
    subscribed_events  VARCHAR(255) NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_webhook_config_merchant ON merchant_webhook_config (merchant_id);

CREATE TABLE webhook_delivery_log (
    webhook_log_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webhook_config_id  BIGINT       NOT NULL REFERENCES merchant_webhook_config (webhook_config_id) ON DELETE CASCADE,
    delivery_id        BIGINT       REFERENCES delivery (delivery_id) ON DELETE SET NULL,
    event_type         VARCHAR(50)  NOT NULL,
    payload            TEXT         NOT NULL,
    http_status_code   INTEGER,
    attempt_count      INTEGER      NOT NULL DEFAULT 1,
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('SUCCESS', 'RETRYING', 'FAILED')),
    error_message      TEXT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_webhook_log_config ON webhook_delivery_log (webhook_config_id);
CREATE INDEX idx_webhook_log_status ON webhook_delivery_log (status);

