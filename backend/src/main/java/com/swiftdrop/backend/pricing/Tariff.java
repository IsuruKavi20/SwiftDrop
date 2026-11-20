package com.swiftdrop.backend.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * The rates a quote uses, all in LKR (except {@code includedKm}).
 * <p>
 * Fee = {@code distanceBase} (covers the first {@code includedKm} of road distance) + {@code perKm} for each km after
 * that + {@code additionalKg} for each kg of total weight above the first + the service surcharge (none for STANDARD).
 * The road distance comes from Google Maps ({@link DistanceSource}). When it can't be found (no API key, Google
 * unreachable, an address it can't route), the district bases take the distance part's place: same district or not.
 *
 * @param version     the published version; 0 while nothing is published (the server's settings apply)
 * @param publishedAt when it was published; null for version 0
 */
public record Tariff(
        int version,
        Instant publishedAt,
        BigDecimal distanceBase,
        BigDecimal includedKm,
        BigDecimal perKm,
        BigDecimal sameDistrictBase,
        BigDecimal otherDistrictBase,
        BigDecimal additionalKg,
        BigDecimal expressSurcharge,
        BigDecimal sameDaySurcharge) {

    public Tariff {
        Objects.requireNonNull(distanceBase, "distanceBase");
        Objects.requireNonNull(includedKm, "includedKm");
        Objects.requireNonNull(perKm, "perKm");
        Objects.requireNonNull(sameDistrictBase, "sameDistrictBase");
        Objects.requireNonNull(otherDistrictBase, "otherDistrictBase");
        Objects.requireNonNull(additionalKg, "additionalKg");
        Objects.requireNonNull(expressSurcharge, "expressSurcharge");
        Objects.requireNonNull(sameDaySurcharge, "sameDaySurcharge");
    }

    /**
     * SwiftDrop's starting rates as version 0: LKR 250 for the first 5 km, LKR 6 per km after that, district bases
     * 350 / 650 when the distance is unknown, LKR 120 per additional kg, 350 Express, 650 Same day.
     */
    public static Tariff defaults() {
        return new Tariff(0, null, new BigDecimal("250.00"), new BigDecimal("5.00"), new BigDecimal("6.00"),
                new BigDecimal("350.00"), new BigDecimal("650.00"), new BigDecimal("120.00"),
                new BigDecimal("350.00"), new BigDecimal("650.00"));
    }

    /** True while nothing has been published and the server's settings apply. */
    public boolean isDefault() {
        return version == 0;
    }
}
