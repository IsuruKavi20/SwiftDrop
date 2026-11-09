package com.swiftdrop.backend.notification.repository;

import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;
import com.swiftdrop.backend.notification.entity.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {
    List<WebhookDeliveryLog> findByWebhookConfig_Id(Long webhookConfigId);
    List<WebhookDeliveryLog> findByDelivery_Id(Long deliveryId);
    List<WebhookDeliveryLog> findByStatus(WebhookStatus status);
}

