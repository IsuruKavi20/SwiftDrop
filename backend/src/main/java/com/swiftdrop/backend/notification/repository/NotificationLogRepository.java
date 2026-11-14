package com.swiftdrop.backend.notification.repository;

import com.swiftdrop.backend.notification.entity.NotificationLog;
import com.swiftdrop.backend.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByDelivery_Id(Long deliveryId);
    Page<NotificationLog> findByRecipientContact(String recipientContact, Pageable pageable);
    List<NotificationLog> findByStatus(NotificationStatus status);

    @Query("SELECT n FROM NotificationLog n " +
           "LEFT JOIN n.delivery d " +
           "LEFT JOIN d.customer c " +
           "WHERE c.id = :customerId " +
           "OR n.recipientContact = :phone " +
           "OR n.recipientContact = :email " +
           "ORDER BY n.createdAt DESC")
    Page<NotificationLog> findForCustomer(
            @Param("customerId") Long customerId,
            @Param("phone") String phone,
            @Param("email") String email,
            Pageable pageable
    );

    @Query("SELECT COUNT(n) FROM NotificationLog n " +
           "LEFT JOIN n.delivery d " +
           "LEFT JOIN d.customer c " +
           "WHERE c.id = :customerId " +
           "OR n.recipientContact = :phone " +
           "OR n.recipientContact = :email")
    long countForCustomer(
            @Param("customerId") Long customerId,
            @Param("phone") String phone,
            @Param("email") String email
    );
}
