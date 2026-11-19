-- Distance-based pricing: the fee follows the road distance from Google Maps (Routes API).
-- Fee = distance_base (covers the first included_km) + per_km × km after that + weight + service surcharge.
-- same_district_base / other_district_base stay: they replace the distance part when Google can't give a distance.
-- Versions published before this get SwiftDrop's starting distance rates (LKR 250 for 5 km, LKR 6 per km after).

ALTER TABLE pricing_version ADD COLUMN distance_base NUMERIC(12,2) NOT NULL DEFAULT 250.00;
ALTER TABLE pricing_version ADD COLUMN included_km   NUMERIC(8,2)  NOT NULL DEFAULT 5.00;
ALTER TABLE pricing_version ADD COLUMN per_km        NUMERIC(12,2) NOT NULL DEFAULT 6.00;

ALTER TABLE pricing_version ADD CONSTRAINT chk_pricing_distance_rates_not_negative
    CHECK (distance_base >= 0 AND included_km >= 0 AND per_km >= 0);
