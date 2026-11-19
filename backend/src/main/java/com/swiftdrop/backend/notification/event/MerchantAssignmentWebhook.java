package com.swiftdrop.backend.notification.event;

import java.time.Instant;

/** Internal routing envelope for a Merchant courier assignment. */
public record MerchantAssignmentWebhook(Long merchantId, Payload payload) {
    public static final String EVENT_TYPE = "COURIER_ASSIGNED";
    public static final String ROUTING_KEY = "merchant.webhook.courier-assigned";

    public record Payload(String eventId, Long deliveryId, String trackingNumber, String eventType, Instant timestamp) {
    }
}
