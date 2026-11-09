package com.swiftdrop.backend.notification.repository;

import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantWebhookConfigRepository extends JpaRepository<MerchantWebhookConfig, Long> {
    List<MerchantWebhookConfig> findByMerchant_IdAndActiveTrue(Long merchantId);
    List<MerchantWebhookConfig> findByActiveTrue();
}

