package com.swiftdrop.backend.notification.dto;

import com.swiftdrop.backend.notification.entity.NotificationChannel;
import com.swiftdrop.backend.notification.entity.NotificationLog;
import com.swiftdrop.backend.notification.entity.NotificationStatus;

import java.time.Instant;

/**
 * Stable JSON representation of a notification sent to or intended for a user.
 */
public record NotificationResponse(
        Long id,
        Long deliveryId,
        String trackingNumber,
        NotificationChannel channel,
        String eventType,
        String subject,
        String content,
        NotificationStatus status,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(NotificationLog log) {
        Long deliveryId = log.getDelivery() != null ? log.getDelivery().getId() : null;
        String trackingNumber = log.getDelivery() != null ? log.getDelivery().getTrackingNumber() : null;

        return new NotificationResponse(
                log.getId(),
                deliveryId,
                trackingNumber,
                log.getChannel(),
                log.getEventType(),
                log.getSubject(),
                log.getContent(),
                log.getStatus(),
                log.getCreatedAt(),
                log.getSentAt()
        );
    }
}

