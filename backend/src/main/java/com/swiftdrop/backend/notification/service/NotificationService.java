package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.customer.repository.CustomerRepository;
import com.swiftdrop.backend.notification.dto.NotificationResponse;
import com.swiftdrop.backend.notification.entity.NotificationChannel;
import com.swiftdrop.backend.notification.entity.NotificationLog;
import com.swiftdrop.backend.notification.entity.RecipientRole;
import com.swiftdrop.backend.notification.repository.NotificationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationLogRepository notificationLogs;
    private final CustomerRepository customerRepository;
    private final CustomerDeliveryRepository deliveryRepository;
    private final NotificationPublisherService publisher;

    public NotificationService(NotificationLogRepository notificationLogs,
                               CustomerRepository customerRepository,
                               CustomerDeliveryRepository deliveryRepository,
                               NotificationPublisherService publisher) {
        this.notificationLogs = notificationLogs;
        this.customerRepository = customerRepository;
        this.deliveryRepository = deliveryRepository;
        this.publisher = publisher;
    }

    /**
     * Retrieves paginated notifications matching the authenticated customer's deliveries, phone, or email.
     */
    public PageResponse<NotificationResponse> getMyNotifications(UUID authUserId, String emailClaim, int page, int size) {
        Customer customer = customerRepository.findByUserId(authUserId).orElse(null);

        Long customerId = customer != null ? customer.getId() : null;
        String phone = customer != null ? customer.getPhoneNumber() : null;

        if (customerId == null && phone == null && emailClaim == null) {
            return new PageResponse<>(Collections.emptyList(), page, size, 0, 0);
        }

        Page<NotificationLog> logPage = notificationLogs.findForCustomer(
                customerId,
                phone,
                emailClaim,
                PageRequest.of(Math.max(0, page), Math.max(1, size))
        );

        return PageResponse.from(logPage, NotificationResponse::from);
    }

    /**
     * Returns total notification count for badge display.
     */
    public long getNotificationCount(UUID authUserId, String emailClaim) {
        Customer customer = customerRepository.findByUserId(authUserId).orElse(null);

        Long customerId = customer != null ? customer.getId() : null;
        String phone = customer != null ? customer.getPhoneNumber() : null;

        if (customerId == null && phone == null && emailClaim == null) {
            return 0L;
        }

        return notificationLogs.countForCustomer(customerId, phone, emailClaim);
    }

    /**
     * Dev helper: creates a sample notification for testing the frontend UI.
     */
    @Transactional
    public NotificationResponse triggerTestNotification(UUID authUserId, String emailClaim, String trackingNumber, String status) {
        Customer customer = customerRepository.findByUserId(authUserId).orElse(null);
        String contact = emailClaim != null ? emailClaim : (customer != null ? customer.getPhoneNumber() : "test@swiftdrop.me");

        Delivery delivery = null;
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            delivery = deliveryRepository.findByTrackingNumber(trackingNumber).orElse(null);
        }

        String eventStatus = status != null && !status.isBlank() ? status.toUpperCase() : "IN_TRANSIT";
        String trk = trackingNumber != null ? trackingNumber : "SD-DEMO-001";

        NotificationLog logEntry = new NotificationLog(
                delivery,
                contact,
                RecipientRole.CUSTOMER,
                NotificationChannel.IN_APP,
                eventStatus,
                "Delivery Update: Parcel #" + trk + " is " + eventStatus,
                "Your package #" + trk + " is now " + eventStatus + ". Thank you for choosing SwiftDrop!"
        );
        logEntry.markSent(Instant.now());

        NotificationLog saved = notificationLogs.save(logEntry);

        // Also publish to RabbitMQ to test end-to-end email and SMS dispatching
        String targetEmail = (emailClaim != null && !emailClaim.isBlank())
                ? emailClaim
                : (customer != null && customer.getEmail() != null ? customer.getEmail() : null);
        publisher.publishTestStatusChanged(trk, eventStatus, targetEmail);

        return NotificationResponse.from(saved);
    }
}

