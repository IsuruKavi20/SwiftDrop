-- Pricing module: the tariff an admin publishes (Pricing page). Each publish adds a new version; old versions stay
-- for the record. Quotes use the highest version; with no rows, the server's settings (swiftdrop.customer.pricing.*).
-- Bookings store their own fee (delivery.estimated_fee), so publishing never changes a parcel already booked.

CREATE TABLE pricing_version (
    pricing_version_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version              INTEGER       NOT NULL UNIQUE,      -- 1, 2, 3…; the UNIQUE stops two admins publishing the same number
    same_district_base   NUMERIC(12,2) NOT NULL,
    other_district_base  NUMERIC(12,2) NOT NULL,
    additional_kg        NUMERIC(12,2) NOT NULL,             -- for each kg of total weight above the first
    express_surcharge    NUMERIC(12,2) NOT NULL,
    same_day_surcharge   NUMERIC(12,2) NOT NULL,
    published_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    published_by         UUID          NOT NULL REFERENCES app_users (id),
    CONSTRAINT chk_pricing_version_positive CHECK (version > 0),
    CONSTRAINT chk_pricing_rates_not_negative CHECK (
        same_district_base >= 0 AND other_district_base >= 0 AND additional_kg >= 0
        AND express_surcharge >= 0 AND same_day_surcharge >= 0)
);
