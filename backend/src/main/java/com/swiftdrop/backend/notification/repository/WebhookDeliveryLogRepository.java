package com.swiftdrop.backend.notification.repository;

import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;
import com.swiftdrop.backend.notification.entity.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {
    List<WebhookDeliveryLog> findByWebhookConfig_Id(Long webhookConfigId);
    List<WebhookDeliveryLog> findByWebhookConfig_IdOrderByCreatedAtDesc(Long webhookConfigId);
    List<WebhookDeliveryLog> findTop50ByWebhookConfig_Merchant_IdOrderByCreatedAtDesc(Long merchantId);
    List<WebhookDeliveryLog> findByDelivery_Id(Long deliveryId);
    List<WebhookDeliveryLog> findByStatus(WebhookStatus status);
}
