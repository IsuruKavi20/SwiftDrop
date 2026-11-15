package com.swiftdrop.backend.notification.dto;

import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;

import java.time.Instant;

public record WebhookConfigResponse(
        Long id,
        String endpointUrl,
        String secretKey,
        String subscribedEvents,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static WebhookConfigResponse from(MerchantWebhookConfig config) {
        return new WebhookConfigResponse(
                config.getId(),
                config.getEndpointUrl(),
                config.getSecretKey(),
                config.getSubscribedEvents(),
                config.isActive(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
