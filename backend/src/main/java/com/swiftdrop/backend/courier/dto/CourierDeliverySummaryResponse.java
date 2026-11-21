package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Summary DTO for one row in the Courier's "My Deliveries" list.
 */
public record CourierDeliverySummaryResponse(
        Long id,
        String trackingNumber,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        String city,
        String postalCode,
        DeliveryStatus status,
        String specialInstructions,
        Instant assignedAt,
        boolean isToday
) {

    public static CourierDeliverySummaryResponse from(Delivery delivery) {
        DeliveryAddress dest = delivery.getDestinationAddress().orElse(null);
        boolean today = delivery.getCreatedAt() != null &&
                delivery.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());

        return new CourierDeliverySummaryResponse(
                delivery.getId(),
                delivery.getTrackingNumber(),
                dest != null ? dest.getContactName() : null,
                dest != null ? dest.getContactPhone() : null,
                dest != null ? dest.getStreet() : null,
                dest != null ? dest.getCity() : null,
                dest != null ? dest.getPostalCode() : null,
                delivery.getStatus(),
                delivery.getSpecialInstructions(),
                delivery.getCreatedAt(),
                today
        );
    }
}
