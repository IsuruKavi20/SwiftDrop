package com.swiftdrop.backend.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** The five rates to publish, in LKR: zero or more, under 1,000,000, at most 2 decimals. */
public record PricingRatesRequest(
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
