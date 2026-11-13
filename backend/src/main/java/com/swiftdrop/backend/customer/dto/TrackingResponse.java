package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Track Parcel and Public Tracking. Safe to show to anyone holding the tracking number or link, so it holds
 * NO private data: no names, phone numbers, street addresses, parcel contents or public token, only the cities.
 */
public record TrackingResponse(
        String trackingNumber,
        DeliveryType deliveryType,
        DeliveryStatus status,
        String fromCity,
        String toCity,
        LocalDate estimatedDeliveryDate,
        Instant deliveredAt,
        List<TrackingEventResponse> events) {

    /** Reads the lazy child collections: call inside a transaction. */
    public static TrackingResponse from(Delivery delivery) {
        return new TrackingResponse(
                delivery.getTrackingNumber(),
                delivery.getDeliveryType(),
                delivery.getStatus(),
                delivery.getPickupAddress().map(DeliveryAddress::getCity).orElse(null),
                delivery.getDestinationAddress().map(DeliveryAddress::getCity).orElse(null),
                delivery.getEstimatedDeliveryDate(),
                delivery.getDeliveredAt(),
                delivery.getTrackingEvents().stream().map(TrackingEventResponse::from).toList());
    }

    /** Public links must never leak arbitrary courier notes or detailed location names. */
    public static TrackingResponse publicFrom(Delivery delivery) {
        TrackingResponse details = from(delivery);
        List<TrackingEventResponse> safeEvents = details.events().stream()
                .map(event -> new TrackingEventResponse(event.status(), publicDescription(event.status()), null,
                        event.eventTime())).toList();
        return new TrackingResponse(details.trackingNumber(), details.deliveryType(), details.status(),
                details.fromCity(), details.toCity(), details.estimatedDeliveryDate(),
                details.deliveredAt(), safeEvents);
    }

    private static String publicDescription(DeliveryStatus status) {
        return switch (status) {
            case PENDING -> "Booking created";
            case CONFIRMED -> "Booking confirmed";
            case PICKED_UP -> "Parcel picked up";
            case IN_TRANSIT -> "Parcel in transit";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Parcel delivered";
            case FAILED -> "Delivery attempt unsuccessful";
            case CANCELLED -> "Booking cancelled";
        };
    }
}
