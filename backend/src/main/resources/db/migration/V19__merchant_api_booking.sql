-- One durable idempotency and actor record per machine-created delivery.
CREATE TABLE merchant_api_booking (
    delivery_id      BIGINT PRIMARY KEY REFERENCES delivery (delivery_id),
    merchant_id      BIGINT NOT NULL REFERENCES merchant (merchant_id),
    client_id        VARCHAR(255) NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    request_hash     VARCHAR(64) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_merchant_api_booking_key UNIQUE (merchant_id, client_id, idempotency_key)
);
