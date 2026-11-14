package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.notification.service.NotificationPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/** Bridges customer booking/cancellation to the existing team RabbitMQ status-event publisher.
 * Notifications never run inside the booking transaction and cannot roll back a successful booking.
 * In a production system, use an outbox for guaranteed retry after RabbitMQ outages.
 */
@Component
public class CustomerStatusNotificationBridge {
    private static final Logger log = LoggerFactory.getLogger(CustomerStatusNotificationBridge.class);
    private final CustomerDeliveryRepository deliveries;
    private final NotificationPublisherService publisher;

    public CustomerStatusNotificationBridge(CustomerDeliveryRepository deliveries, NotificationPublisherService publisher) {
        this.deliveries = deliveries;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onCustomerStatusChanged(CustomerStatusChange event) {
        try {
            deliveries.findById(event.deliveryId()).ifPresent(delivery -> {
                TrackingEvent latestEvent = null;
                if (event.trackingEventId() != null) {
                    latestEvent = delivery.getTrackingEvents().stream()
                            .filter(tracking -> Objects.equals(event.trackingEventId(), tracking.getId()))
                            .findFirst()
                            .orElse(null);
                }
                if (latestEvent == null && !delivery.getTrackingEvents().isEmpty()) {
                    latestEvent = delivery.getTrackingEvents().get(delivery.getTrackingEvents().size() - 1);
                }
                publisher.publishDeliveryStatusChanged(delivery, latestEvent);
            });
        } catch (Exception ex) {
            log.warn("Could not publish customer status event for delivery {}: {}",
                    event.deliveryId(), ex.getMessage(), ex);
        }
    }
}
