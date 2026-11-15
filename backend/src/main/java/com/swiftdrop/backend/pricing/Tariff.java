package com.swiftdrop.backend.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * The rates a quote uses, all in LKR. Fee = base (same district or not) + {@code additionalKg} for each kg of total
 * weight above the first + the service surcharge (none for STANDARD).
 *
 * @param version     the published version; 0 while nothing is published (the server's settings apply)
 * @param publishedAt when it was published; null for version 0
 */
public record Tariff(
        int version,
        Instant publishedAt,
        BigDecimal sameDistrictBase,
        BigDecimal otherDistrictBase,
        BigDecimal additionalKg,
        BigDecimal expressSurcharge,
        BigDecimal sameDaySurcharge) {

    public Tariff {
        Objects.requireNonNull(sameDistrictBase, "sameDistrictBase");
        Objects.requireNonNull(otherDistrictBase, "otherDistrictBase");
        Objects.requireNonNull(additionalKg, "additionalKg");
        Objects.requireNonNull(expressSurcharge, "expressSurcharge");
        Objects.requireNonNull(sameDaySurcharge, "sameDaySurcharge");
    }

    /** The rates SwiftDrop started with (LKR 350 / 650 / 120 per kg / 350 express / 650 same day), as version 0. */
    public static Tariff defaults() {
        return new Tariff(0, null, new BigDecimal("350.00"), new BigDecimal("650.00"), new BigDecimal("120.00"),
                new BigDecimal("350.00"), new BigDecimal("650.00"));
    }

    /** True while nothing has been published and the server's settings apply. */
    public boolean isDefault() {
        return version == 0;
    }
}
