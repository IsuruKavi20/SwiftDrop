-- Customer Module: customers, saved addresses, deliveries, parcels, delivery addresses and tracking events.
-- Couriers are users of the SwiftDrop auth service, so courier ids are UUIDs (the token's "sub"), not a foreign key.

CREATE TABLE customer (
    customer_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auth_user_id  UUID         NOT NULL UNIQUE,  -- "sub" claim of the customer's access token
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    phone_number  VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Addresses a customer saves to reuse when booking
CREATE TABLE customer_address (
    address_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT       NOT NULL REFERENCES customer (customer_id) ON DELETE CASCADE,
    label        VARCHAR(50)  NOT NULL,          -- e.g. "Home", "Office"
    street       VARCHAR(255) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    district     VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(15),
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_customer_address_customer ON customer_address (customer_id);

CREATE TABLE delivery (
    delivery_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id              BIGINT       NOT NULL REFERENCES customer (customer_id),
    courier_id               UUID,                        -- assigned courier's auth user id
    tracking_number          VARCHAR(30)  NOT NULL UNIQUE,
    public_tracking_token    VARCHAR(64)  NOT NULL UNIQUE, -- random; for public tracking links
    delivery_type            VARCHAR(20)  NOT NULL
        CHECK (delivery_type IN ('STANDARD', 'EXPRESS', 'SAME_DAY')),
    status                   VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING', 'CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
                          'DELIVERED', 'FAILED', 'CANCELLED')),
    special_instructions     VARCHAR(500),
    estimated_delivery_date  DATE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at             TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_delivery_customer ON delivery (customer_id, created_at);
CREATE INDEX idx_delivery_courier ON delivery (courier_id);

CREATE TABLE parcel (
    parcel_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id     BIGINT        NOT NULL REFERENCES delivery (delivery_id) ON DELETE CASCADE,
    description     VARCHAR(255)  NOT NULL,
    weight_kg       DECIMAL(10,2) NOT NULL CHECK (weight_kg > 0),
    length_cm       DECIMAL(10,2) NOT NULL CHECK (length_cm > 0),
    width_cm        DECIMAL(10,2) NOT NULL CHECK (width_cm > 0),
    height_cm       DECIMAL(10,2) NOT NULL CHECK (height_cm > 0),
    declared_value  DECIMAL(12,2) CHECK (declared_value >= 0)
);

CREATE INDEX idx_parcel_delivery ON parcel (delivery_id);

-- The pickup and destination details as they were at booking; editing a saved address never changes these
CREATE TABLE delivery_address (
    delivery_address_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id          BIGINT       NOT NULL REFERENCES delivery (delivery_id) ON DELETE CASCADE,
    address_type         VARCHAR(20)  NOT NULL CHECK (address_type IN ('PICKUP', 'DESTINATION')),
    contact_name         VARCHAR(100) NOT NULL,   -- sender (pickup) or receiver (destination)
    contact_phone        VARCHAR(20)  NOT NULL,
    street               VARCHAR(255) NOT NULL,
    city                 VARCHAR(100) NOT NULL,
    district             VARCHAR(100) NOT NULL,
    postal_code          VARCHAR(15),
    CONSTRAINT uq_delivery_address_type UNIQUE (delivery_id, address_type)
);

CREATE TABLE tracking_event (
    tracking_event_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id        BIGINT       NOT NULL REFERENCES delivery (delivery_id) ON DELETE CASCADE,
    status             VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING', 'CONFIRMED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
                          'DELIVERED', 'FAILED', 'CANCELLED')),
    description        VARCHAR(255) NOT NULL,
    location_name      VARCHAR(255),
    event_time         TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by        UUID                                -- auth user id of the courier or admin
);

-- The tracking timeline: one delivery's events in time order
CREATE INDEX idx_tracking_event_timeline ON tracking_event (delivery_id, event_time);
