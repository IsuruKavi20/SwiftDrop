package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.customer.entity.Parcel;

import java.math.BigDecimal;

/**
 * Parcel details for courier delivery operations.
 */
public record CourierParcelResponse(
        Long id,
        String description,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal declaredValue
) {

    public static CourierParcelResponse from(Parcel parcel) {
        return new CourierParcelResponse(
                parcel.getId(),
                parcel.getDescription(),
                parcel.getWeightKg(),
                parcel.getLengthCm(),
                parcel.getWidthCm(),
                parcel.getHeightCm(),
                parcel.getDeclaredValue()
        );
    }
}
