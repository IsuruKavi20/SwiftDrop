-- Identity module: a read-only copy of the people in the SwiftDrop auth service.
-- Only the identity module writes these tables (from the auth Directory API). Other modules only point to them.
-- No email here on purpose: it's asked from the auth service live, only when an email must be sent.

CREATE TABLE app_users (
    id             UUID         PRIMARY KEY,              -- the auth user id = the token's "sub"
    name           VARCHAR(200),                          -- null when unknown yet or the person was deleted
    picture_url    VARCHAR(2048),
    status         VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING_SYNC', 'ACTIVE', 'LOCKED', 'DISABLED', 'DELETED')),
    active         BOOLEAN      NOT NULL DEFAULT FALSE,   -- may log in and be used (auth's "active")
    last_modified  TIMESTAMP WITH TIME ZONE,              -- auth's meta.lastModified of this person
    synced_at      TIMESTAMP WITH TIME ZONE               -- when we last copied this row from auth
);

CREATE TABLE app_user_roles (
    user_id  UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role     VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- "List all couriers": find users by role
CREATE INDEX idx_app_user_roles_role ON app_user_roles (role);

-- Placeholders for every person the customer module already refers to, so foreign keys can be added later (V3).
-- The sync fills in the real details on start-up.
INSERT INTO app_users (id, status)
SELECT known.user_id, 'PENDING_SYNC'
FROM (
    SELECT auth_user_id AS user_id FROM customer
    UNION
    SELECT courier_id FROM delivery WHERE courier_id IS NOT NULL
    UNION
    SELECT recorded_by FROM tracking_event WHERE recorded_by IS NOT NULL
) AS known;
