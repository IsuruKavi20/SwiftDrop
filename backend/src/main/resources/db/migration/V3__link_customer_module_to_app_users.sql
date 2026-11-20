-- Customer module now links to people (identity module) instead of keeping plain UUIDs and its own copy of names.

-- 1. Anyone the customer tables refer to who has no app_users row yet (e.g. registered after V2 ran) gets a
--    placeholder; the identity refresh fills in the real details. Without this the foreign keys below would fail.
INSERT INTO app_users (id, status)
SELECT known.user_id, 'PENDING_SYNC'
FROM (
    SELECT auth_user_id AS user_id FROM customer
    UNION
    SELECT courier_id FROM delivery WHERE courier_id IS NOT NULL
    UNION
    SELECT recorded_by FROM tracking_event WHERE recorded_by IS NOT NULL
) AS known
WHERE NOT EXISTS (SELECT 1 FROM app_users a WHERE a.id = known.user_id);

-- 2. Real links: the database now refuses an id that isn't a known person
ALTER TABLE customer
    ADD CONSTRAINT fk_customer_user FOREIGN KEY (auth_user_id) REFERENCES app_users (id);
ALTER TABLE delivery
    ADD CONSTRAINT fk_delivery_courier FOREIGN KEY (courier_id) REFERENCES app_users (id);
ALTER TABLE tracking_event
    ADD CONSTRAINT fk_tracking_event_recorded_by FOREIGN KEY (recorded_by) REFERENCES app_users (id);

-- 3. Name and email belong to the login account (auth service): no second copy here that could disagree
ALTER TABLE customer DROP COLUMN first_name;
ALTER TABLE customer DROP COLUMN last_name;
ALTER TABLE customer DROP COLUMN email;
