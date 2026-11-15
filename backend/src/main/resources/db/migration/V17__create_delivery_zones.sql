-- Dispatcher module: delivery zones (Delivery Zones page). A zone groups Sri Lankan districts; each district belongs
-- to one zone at most. Zones don't change prices or bookings yet: what they control is a later team decision.

CREATE TABLE delivery_zone (
    zone_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    code        VARCHAR(4)   NOT NULL UNIQUE,                -- 2 to 4 capital letters, e.g. WP
    active      BOOLEAN      NOT NULL,
    version     BIGINT       NOT NULL,                       -- optimistic locking (@Version)
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE delivery_zone_district (
    zone_id   BIGINT      NOT NULL REFERENCES delivery_zone (zone_id),
    district  VARCHAR(30) NOT NULL UNIQUE,                   -- UNIQUE: a district is in one zone at most
    PRIMARY KEY (zone_id, district)
);
