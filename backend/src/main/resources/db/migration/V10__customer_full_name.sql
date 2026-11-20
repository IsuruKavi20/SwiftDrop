-- The customer's full name, entered when they create their customer profile. Shown on the dashboard
-- ("Welcome back, <name>") and in the navbar, even when the login account has no name.

-- 1. Separate first/last name columns are not used: V3 dropped them, but the shared dev database has empty copies
--    again (added outside Flyway). IF EXISTS keeps this working on databases that don't have them.
ALTER TABLE customer DROP COLUMN IF EXISTS first_name;
ALTER TABLE customer DROP COLUMN IF EXISTS last_name;

-- 2. One column for the whole name, e.g. "Nimal Perera"
ALTER TABLE customer ADD COLUMN full_name VARCHAR(150);

-- 3. Existing customers start with the name from their login account (still empty if that has none)
UPDATE customer c
SET full_name = a.name
FROM app_users a
WHERE a.id = c.auth_user_id AND a.name IS NOT NULL AND TRIM(a.name) <> '';
