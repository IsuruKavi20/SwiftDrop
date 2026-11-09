package com.swiftdrop.backend.notification.repository;

import com.swiftdrop.backend.notification.entity.NotificationChannel;
import com.swiftdrop.backend.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByEventTypeAndChannelAndActiveTrue(String eventType, NotificationChannel channel);
}

