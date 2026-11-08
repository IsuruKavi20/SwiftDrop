-- Merchant module: the owner is a real person (identity's app_users), and a profile can exist before it is filled in
-- (it is created automatically on the merchant's first visit, then completed in the app).

-- Anyone the merchant table already refers to gets an app_users placeholder, so the link below can be added
INSERT INTO app_users (id, status)
SELECT m.auth_user_id, 'PENDING_SYNC'
FROM merchant m
WHERE NOT EXISTS (SELECT 1 FROM app_users a WHERE a.id = m.auth_user_id);

ALTER TABLE merchant
    ADD CONSTRAINT fk_merchant_owner FOREIGN KEY (auth_user_id) REFERENCES app_users (id);

-- Empty until the merchant completes their business profile
ALTER TABLE merchant ALTER COLUMN business_name DROP NOT NULL;
ALTER TABLE merchant ALTER COLUMN business_email DROP NOT NULL;
ALTER TABLE merchant ALTER COLUMN contact_number DROP NOT NULL;
ALTER TABLE merchant ALTER COLUMN address_line_1 DROP NOT NULL;
ALTER TABLE merchant ALTER COLUMN city DROP NOT NULL;
ALTER TABLE merchant ALTER COLUMN country DROP NOT NULL;
