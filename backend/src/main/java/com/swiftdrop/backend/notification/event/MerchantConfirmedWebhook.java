package com.swiftdrop.backend.notification.event;

import java.time.Instant;

/** Internal broker envelope. Only the payload is sent to the Merchant endpoint. */
public record MerchantConfirmedWebhook(Long merchantId, Payload payload) {
    public static final String STATUS = "CONFIRMED";
    public static final String ROUTING_KEY = "merchant.webhook.confirmed";

    public record Payload(String eventId, Long deliveryId, String trackingNumber, String status, Instant timestamp) {
    }
}
