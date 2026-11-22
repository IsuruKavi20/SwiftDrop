package com.swiftdrop.backend.notification.event;

import java.time.Instant;

/** The merchant ID is used for routing and stays off the wire sent to the endpoint. */
public record MerchantLifecycleWebhook(Long merchantId, Payload payload) {
    public static final String ROUTING_KEY = "merchant.webhook.lifecycle";

    public record Payload(String eventId, Long deliveryId, String trackingNumber, String status, Instant timestamp) {
    }
}
