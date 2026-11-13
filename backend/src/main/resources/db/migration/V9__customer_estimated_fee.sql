-- Store the customer-confirmed quote; existing bookings have no retroactively invented amount.
ALTER TABLE delivery ADD COLUMN estimated_fee NUMERIC(12,2);
ALTER TABLE delivery ADD CONSTRAINT chk_delivery_estimated_fee CHECK (estimated_fee IS NULL OR estimated_fee >= 0);
