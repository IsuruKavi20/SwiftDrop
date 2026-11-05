package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Parcel;

import java.math.BigDecimal;

public record ParcelResponse(
        Long id,
        String description,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal declaredValue) {

    public static ParcelResponse from(Parcel parcel) {
        return new ParcelResponse(parcel.getId(), parcel.getDescription(), parcel.getWeightKg(), parcel.getLengthCm(),
                parcel.getWidthCm(), parcel.getHeightCm(), parcel.getDeclaredValue());
    }
}
