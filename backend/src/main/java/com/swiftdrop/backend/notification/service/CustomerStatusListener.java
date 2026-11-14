package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.service.CustomerStatusChange;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to customer's "status changed" event and hands it to the RabbitMQ publisher.
 * Customer only publishes the event and does not know this class exists (no cycle between the modules).
 *
 * <p>Reliable (outbox): {@code @ApplicationModuleListener} runs after the booking is committed, in the background, in
 * its own transaction, and Spring Modulith records the event in {@code event_publication} together with the booking.
 * If this method throws (e.g. RabbitMQ is down), the record stays open and {@link OutboxResubmitter} sends it again.
 * So failures must be thrown, not swallowed.
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

    @ApplicationModuleListener
    void on(CustomerStatusChange event) {
        Delivery delivery = entities.find(Delivery.class, event.deliveryId());
        if (delivery == null) {
            log.info("Delivery {} no longer exists; status event skipped", event.deliveryId());
            return;
        }
        delivery.getTrackingEvents().stream()
                .filter(tracking -> event.trackingEventId().equals(tracking.getId()))
                .findFirst()
                .ifPresent(tracking -> publisher.publishDeliveryStatusChanged(delivery, tracking));
    }
}
