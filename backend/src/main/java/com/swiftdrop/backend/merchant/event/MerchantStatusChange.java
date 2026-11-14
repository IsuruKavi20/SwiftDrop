package com.swiftdrop.backend.merchant.event;

import org.springframework.modulith.NamedInterface;

/**
 * Event: "a merchant delivery's status changed". Published by merchant after the change is saved; any module may listen
 * (notification sends messages). Merchant does not know who listens.
 */
@NamedInterface("events")
public record MerchantStatusChange(Long deliveryId, Long trackingEventId) {
}
