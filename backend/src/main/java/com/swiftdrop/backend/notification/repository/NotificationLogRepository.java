package com.swiftdrop.backend.notification.repository;

import com.swiftdrop.backend.notification.entity.NotificationLog;
import com.swiftdrop.backend.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByDelivery_Id(Long deliveryId);
    Page<NotificationLog> findByRecipientContact(String recipientContact, Pageable pageable);
    List<NotificationLog> findByStatus(NotificationStatus status);
}

