
-- Merchant Module: business profiles linked to authenticated users.

CREATE TABLE merchant (
    merchant_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auth_user_id    UUID         NOT NULL UNIQUE,

    business_name   VARCHAR(150) NOT NULL,
    business_email  VARCHAR(150) NOT NULL,
    contact_number  VARCHAR(20)  NOT NULL,

    address_line_1  VARCHAR(255) NOT NULL,
    address_line_2  VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    province        VARCHAR(100),
    postal_code     VARCHAR(20),
    country         VARCHAR(100) NOT NULL,

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchant_business_name
    ON merchant (business_name);
