package com.swiftdrop.backend.merchant.event;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;

/** Persisted confirmation snapshot consumed after the booking transaction commits. */
@NamedInterface("events")
public record MerchantDeliveryConfirmed(Long deliveryId, Long trackingEventId, Long merchantId,
                                        String trackingNumber, Instant timestamp) {
}
