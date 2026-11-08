-- Dispatcher module (PR #8): couriers' work profiles and which courier carries which delivery.
-- Who the courier is (name, roles, active) stays in identity's app_users; this keeps only their work details.

CREATE TABLE courier (
    courier_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auth_user_id    UUID         NOT NULL UNIQUE REFERENCES app_users (id),
    phone_number    VARCHAR(20)  NOT NULL,
    vehicle_type    VARCHAR(30),
    vehicle_number  VARCHAR(30),
    availability    VARCHAR(20)  NOT NULL CHECK (availability IN ('AVAILABLE', 'BUSY', 'OFFLINE')),
    version         BIGINT       NOT NULL,                         -- optimistic locking (@Version)
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One row per assignment; history rows keep who ended it and why. active = TRUE only for the current one
CREATE TABLE courier_assignment (
    assignment_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id    BIGINT       NOT NULL REFERENCES delivery (delivery_id),
    courier_id     BIGINT       NOT NULL REFERENCES courier (courier_id),
    assigned_by    UUID         NOT NULL REFERENCES app_users (id),
    assigned_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    active         BOOLEAN,
    ended_by       UUID         REFERENCES app_users (id),
    ended_at       TIMESTAMP WITH TIME ZONE,
    end_reason     VARCHAR(500),
    version        BIGINT       NOT NULL,
    -- At most one active assignment per delivery (NULLs don't clash, so history rows are fine)
    CONSTRAINT uk_assignment_delivery_active UNIQUE (delivery_id, active),
    CONSTRAINT ck_assignment_active_or_ended CHECK (
        (active IS NOT NULL AND active = TRUE AND ended_at IS NULL AND ended_by IS NULL)
        OR (active IS NULL AND ended_at IS NOT NULL AND ended_by IS NOT NULL AND ended_at >= assigned_at))
);

CREATE INDEX idx_assignment_courier_active ON courier_assignment (courier_id, active);
