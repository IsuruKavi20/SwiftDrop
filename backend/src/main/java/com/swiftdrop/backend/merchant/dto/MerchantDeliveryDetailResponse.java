package com.swiftdrop.backend.merchant.dto;

import com.swiftdrop.backend.customer.dto.DeliveryAddressResponse;
import com.swiftdrop.backend.customer.dto.ParcelResponse;
import com.swiftdrop.backend.customer.dto.TrackingEventResponse;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detailed representation of a merchant order: addresses, parcels, and tracking history.
 */
public record MerchantDeliveryDetailResponse(
        Long id,
        String trackingNumber,
        String publicTrackingToken,
        DeliveryType deliveryType,
        DeliveryStatus status,
        String specialInstructions,
        LocalDate estimatedDeliveryDate,
        Instant createdAt,
        Instant deliveredAt,
        DeliveryAddressResponse pickup,
        DeliveryAddressResponse destination,
        List<ParcelResponse> parcels,
        List<TrackingEventResponse> trackingEvents) {

    public static MerchantDeliveryDetailResponse from(Delivery delivery) {
        return new MerchantDeliveryDetailResponse(
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getPublicTrackingToken(),
                delivery.getDeliveryType(),
                delivery.getStatus(),
                delivery.getSpecialInstructions(),
                delivery.getEstimatedDeliveryDate(),
                delivery.getCreatedAt(),
                delivery.getDeliveredAt(),
                delivery.getPickupAddress().map(DeliveryAddressResponse::from).orElse(null),
                delivery.getDestinationAddress().map(DeliveryAddressResponse::from).orElse(null),
                delivery.getParcels().stream().map(ParcelResponse::from).toList(),
                delivery.getTrackingEvents().stream().map(TrackingEventResponse::from).toList());
    }
}
