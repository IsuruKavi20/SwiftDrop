package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.courier.event.CourierDeliveryStatusChanged;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Listens to courier lifecycle events (picked up, in transit, delivered, failed)
 * and forwards them to RabbitMQ for customer/recipient email and SMS dispatch.
 */
@Component
class CourierStatusNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CourierStatusNotificationListener.class);

    private final EntityManager entities;
    private final NotificationPublisherService publisher;

    CourierStatusNotificationListener(EntityManager entities, NotificationPublisherService publisher) {
        this.entities = entities;
        this.publisher = publisher;
    }

    @ApplicationModuleListener
    void on(CourierDeliveryStatusChanged event) {
        Delivery delivery = entities.find(Delivery.class, event.deliveryId());
        if (delivery == null) {
            log.info("Delivery {} no longer exists; courier status event skipped", event.deliveryId());
            return;
        }
        publisher.publishDeliveryStatusChanged(delivery, trackingEventFor(delivery, event.trackingEventId()));
    }

    private static TrackingEvent trackingEventFor(Delivery delivery, Long trackingEventId) {
        List<TrackingEvent> events = delivery.getTrackingEvents();
        if (trackingEventId != null) {
            for (TrackingEvent tracking : events) {
                if (Objects.equals(trackingEventId, tracking.getId())) {
                    return tracking;
                }
            }
        }
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
}

