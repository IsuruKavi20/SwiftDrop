package com.swiftdrop.backend.dispatcher.event;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/**
 * Published whenever a courier is successfully assigned to a delivery.
 */
@NamedInterface("events")
public record CourierAssignedToDelivery(Long assignmentId, Long deliveryId, String trackingNumber,
                                        Long courierId, String courierName, String courierPhone,
                                        String destinationAddress, Instant timestamp) {
}

