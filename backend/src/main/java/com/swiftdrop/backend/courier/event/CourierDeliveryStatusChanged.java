package com.swiftdrop.backend.courier.event;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/**
 * Published whenever a courier updates the lifecycle status of any delivery.
 */
@NamedInterface("events")
public record CourierDeliveryStatusChanged(Long deliveryId, Long trackingEventId,
                                           String trackingNumber, DeliveryStatus status, Instant timestamp) {
}

