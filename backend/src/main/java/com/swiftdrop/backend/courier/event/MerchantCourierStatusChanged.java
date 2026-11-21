package com.swiftdrop.backend.courier.event;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.modulith.NamedInterface;

import java.time.Instant;

@NamedInterface("events")
public record MerchantCourierStatusChanged(Long merchantId, Long deliveryId, Long trackingEventId,
                                          String trackingNumber, DeliveryStatus status, Instant timestamp) {
}
