CREATE TABLE merchant_human_booking (
    merchant_id     BIGINT NOT NULL REFERENCES merchant (merchant_id),
    idempotency_key VARCHAR(128) NOT NULL,
    delivery_id     BIGINT NOT NULL UNIQUE REFERENCES delivery (delivery_id) ON DELETE CASCADE,
    request_hash    VARCHAR(64) NOT NULL,
    PRIMARY KEY (merchant_id, idempotency_key)
);
