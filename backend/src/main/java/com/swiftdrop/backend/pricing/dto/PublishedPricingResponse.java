package com.swiftdrop.backend.pricing.dto;

import com.swiftdrop.backend.pricing.Tariff;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The tariff quotes use now. {@code version} 0 means nothing is published yet and the server's settings apply
 * ({@code publishedAt} and {@code publishedBy} are then null).
 */
public record PublishedPricingResponse(int version, Instant publishedAt, String publishedBy, Rates rates) {

    public record Rates(BigDecimal distanceBase, BigDecimal includedKm, BigDecimal perKm,
                        BigDecimal sameDistrictBase, BigDecimal otherDistrictBase, BigDecimal additionalKg,
                        BigDecimal expressSurcharge, BigDecimal sameDaySurcharge) { }

    public static PublishedPricingResponse of(Tariff tariff, String publishedBy) {
        return new PublishedPricingResponse(tariff.version(), tariff.publishedAt(), publishedBy,
                new Rates(tariff.distanceBase(), tariff.includedKm(), tariff.perKm(), tariff.sameDistrictBase(), tariff.otherDistrictBase(), tariff.additionalKg(),
                        tariff.expressSurcharge(), tariff.sameDaySurcharge()));
    }
}
