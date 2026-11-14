package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.customer.service.CustomerStatusChange;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Listens to customer's "status changed" event and hands it to the RabbitMQ publisher. Customer only publishes the
 * event and does not know this class exists (no customer → notification dependency, so no cycle).
 * Runs after the booking is committed, in its own read-only transaction: a broker failure can't undo a booking.
 * Messages can still be lost if RabbitMQ is down; the outbox (roadmap item A) fixes that.
 */
@Component
class CustomerStatusListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatusListener.class);

    private final EntityManager entities;
    private final NotificationPublisherService publisher;

    CustomerStatusListener(EntityManager entities, NotificationPublisherService publisher) {
        this.entities = entities;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    void on(CustomerStatusChange event) {
        try {
            Delivery delivery = entities.find(Delivery.class, event.deliveryId());
            if (delivery == null) {
                return;
            }
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
        } catch (RuntimeException failure) {
            log.warn("Could not publish the status event for delivery {}: {}", event.deliveryId(), failure.getMessage());
        }
    }
}
