package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything about one delivery, for its own customer only: delivery details, the review/confirmation page
 * and the booking confirmation. Includes the public tracking token so the customer can share a tracking link.
 */
public record DeliveryDetailsResponse(
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

    /** Reads the lazy child collections: call inside a transaction. */
    public static DeliveryDetailsResponse from(Delivery delivery) {
        return new DeliveryDetailsResponse(
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
