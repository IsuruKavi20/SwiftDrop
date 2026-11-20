package com.swiftdrop.backend.notification.event;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event published when a courier is assigned to a delivery.
 */
public record CourierAssignedEvent(
    Long deliveryId,
    String trackingNumber,
    Long courierId,
    String courierName,
    String courierPhone,
    String customerName,
    String customerPhone,
    String pickupAddress,
    String destinationAddress,
    Instant assignedAt
) implements Serializable {}

