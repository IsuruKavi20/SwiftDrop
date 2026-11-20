-- Courier Module: operational profile, delivery proof evidence, and delivery failure audit records.

CREATE TABLE courier_profile (
    courier_profile_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auth_user_id       UUID NOT NULL UNIQUE REFERENCES app_users (id),
    phone_number       VARCHAR(30) NOT NULL,
    vehicle_type       VARCHAR(50) NOT NULL,
    assigned_zone      VARCHAR(100) NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE delivery_proof (
    proof_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id         BIGINT NOT NULL UNIQUE REFERENCES delivery (delivery_id),
    courier_id          UUID NOT NULL REFERENCES app_users (id),
    proof_type          VARCHAR(50) NOT NULL,
    recipient_sign_name VARCHAR(100) NOT NULL,
    delivery_notes      TEXT,
    proof_media_url     VARCHAR(2048),
    captured_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE delivery_failure (
    failure_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id     BIGINT NOT NULL REFERENCES delivery (delivery_id),
    courier_id      UUID NOT NULL REFERENCES app_users (id),
    failure_reason  VARCHAR(50) NOT NULL,
    failure_remarks TEXT NOT NULL,
    reported_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_courier_profile_zone ON courier_profile (assigned_zone);
CREATE INDEX idx_delivery_proof_delivery ON delivery_proof (delivery_id);
CREATE INDEX idx_delivery_failure_delivery ON delivery_failure (delivery_id);
