package com.swiftdrop.backend.merchant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One parcel in a merchant delivery booking. Weight in kilograms, dimensions in centimetres. */
public record ParcelRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal weightKg,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal lengthCm,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal widthCm,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal heightCm,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal declaredValue) {
}
