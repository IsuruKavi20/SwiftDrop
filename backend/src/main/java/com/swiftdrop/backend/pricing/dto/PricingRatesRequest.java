package com.swiftdrop.backend.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The rates to publish: zero or more, at most 2 decimals. Money in LKR, under 1,000,000; {@code includedKm} in km,
 * under 10,000.
 */
public record PricingRatesRequest(
        @NotNull(message = "Enter the base fee")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal distanceBase,

        @NotNull(message = "Enter the km the base fee covers")
        @DecimalMin(value = "0", message = "Distance must be zero or more")
        @DecimalMax(value = "9999.99", message = "Distance must be under 10,000 km")
        @Digits(integer = 4, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal includedKm,

        @NotNull(message = "Enter the charge per km")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal perKm,

        @NotNull(message = "Enter the same-district base fee")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal sameDistrictBase,

        @NotNull(message = "Enter the other-district base fee")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal otherDistrictBase,

        @NotNull(message = "Enter the charge per additional kg")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal additionalKg,

        @NotNull(message = "Enter the Express surcharge")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal expressSurcharge,

        @NotNull(message = "Enter the Same day surcharge")
        @DecimalMin(value = "0", message = "Rates must be zero or more")
        @DecimalMax(value = "999999.99", message = "Rates must be under LKR 1,000,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals")
        BigDecimal sameDaySurcharge) { }
