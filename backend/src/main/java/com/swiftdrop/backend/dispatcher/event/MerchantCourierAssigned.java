package com.swiftdrop.backend.dispatcher.event;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/** A persisted assignment for a Merchant-owned delivery. */
@NamedInterface("events")
public record MerchantCourierAssigned(Long assignmentId, Long merchantId, Long deliveryId,
                                      String trackingNumber, Instant timestamp) {
}
