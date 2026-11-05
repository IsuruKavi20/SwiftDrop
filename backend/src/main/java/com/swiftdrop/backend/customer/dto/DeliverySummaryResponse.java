package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;

import java.time.Instant;
import java.time.LocalDate;

/** One row of the My Deliveries list and the dashboard's recent deliveries. */
public record DeliverySummaryResponse(
        Long id,
        String trackingNumber,
        DeliveryType deliveryType,
        DeliveryStatus status,
        String receiverName,
        String destinationCity,
        LocalDate estimatedDeliveryDate,
        Instant createdAt) {

    public static DeliverySummaryResponse from(Delivery delivery) {
        DeliveryAddress destination = delivery.getDestinationAddress().orElse(null);
        return new DeliverySummaryResponse(delivery.getId(), delivery.getTrackingNumber(), delivery.getDeliveryType(),
                delivery.getStatus(),
                destination == null ? null : destination.getContactName(),
                destination == null ? null : destination.getCity(),
                delivery.getEstimatedDeliveryDate(), delivery.getCreatedAt());
    }
}
