-- Merchant delivery ownership: deliveries can belong to either a customer or a merchant.
ALTER TABLE delivery ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE delivery ADD COLUMN merchant_id BIGINT REFERENCES merchant (merchant_id);

ALTER TABLE delivery ADD CONSTRAINT chk_delivery_owner
    CHECK ((customer_id IS NOT NULL AND merchant_id IS NULL) OR (customer_id IS NULL AND merchant_id IS NOT NULL));

CREATE INDEX idx_delivery_merchant ON delivery (merchant_id, created_at);
