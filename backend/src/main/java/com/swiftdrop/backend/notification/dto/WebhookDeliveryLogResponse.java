package com.swiftdrop.backend.notification.dto;

import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;

import java.time.Instant;

public record WebhookDeliveryLogResponse(
        Long id,
        Long webhookConfigId,
        Long deliveryId,
        String eventType,
        String payload,
        Integer httpStatusCode,
        int attemptCount,
        String status,
        String errorMessage,
        Instant createdAt,
        Instant lastAttemptAt
) {
    public static WebhookDeliveryLogResponse from(WebhookDeliveryLog log) {
        return new WebhookDeliveryLogResponse(
                log.getId(),
                log.getWebhookConfig() != null ? log.getWebhookConfig().getId() : null,
                log.getDelivery() != null ? log.getDelivery().getId() : null,
                log.getEventType(),
                log.getPayload(),
                log.getHttpStatusCode(),
                log.getAttemptCount(),
                log.getStatus() != null ? log.getStatus().name() : null,
                log.getErrorMessage(),
                log.getCreatedAt(),
                log.getLastAttemptAt()
        );
    }
}
