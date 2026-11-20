package com.swiftdrop.backend.notification.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Event published when a delivery's lifecycle status changes or key milestones are reached.
 */
public record DeliveryStatusChangedEvent(
    Long deliveryId,
    String trackingNumber,
    String publicTrackingToken,
    String status,
    String description,
    String locationName,
    String customerName,
    String customerEmail,
    String customerPhone,
    String courierName,
    String courierPhone,
    Long merchantId,
    Instant timestamp,
    Map<String, Object> metadata
) implements Serializable {}

