package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.merchant.event.MerchantStatusChange;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to merchant's "status changed" event and hands it to the RabbitMQ publisher.
 * Merchant only publishes the event and does not know this class exists (no merchant → notification dependency).
 */
@Component
class MerchantStatusListener {

    private static final Logger log = LoggerFactory.getLogger(MerchantStatusListener.class);

    private final EntityManager entities;
    private final NotificationPublisherService publisher;

    MerchantStatusListener(EntityManager entities, NotificationPublisherService publisher) {
        this.entities = entities;
        this.publisher = publisher;
    }

    @EventListener
    void on(MerchantStatusChange event) {
        try {
            Delivery delivery = entities.find(Delivery.class, event.deliveryId());
            if (delivery == null) {
                return;
            }
            delivery.getTrackingEvents().stream()
                    .filter(tracking -> event.trackingEventId().equals(tracking.getId()))
                    .findFirst()
                    .ifPresent(tracking -> publisher.publishDeliveryStatusChanged(delivery, tracking));
        } catch (RuntimeException failure) {
            log.warn("Could not publish the status event for merchant delivery {}: {}", event.deliveryId(), failure.getMessage());
        }
    }
}
