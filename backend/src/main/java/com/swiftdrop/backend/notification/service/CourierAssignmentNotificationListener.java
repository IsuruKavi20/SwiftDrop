package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.dispatcher.event.CourierAssignedToDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to dispatcher assignment events and forwards them to RabbitMQ for courier SMS dispatch.
 */
@Component
class CourierAssignmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CourierAssignmentNotificationListener.class);

    private final NotificationPublisherService publisher;

    CourierAssignmentNotificationListener(NotificationPublisherService publisher) {
        this.publisher = publisher;
    }

    @ApplicationModuleListener
    void on(CourierAssignedToDelivery event) {
        log.info("Forwarding courier assignment notification for delivery {} to courier {}", event.deliveryId(), event.courierId());
        publisher.publishCourierAssigned(
                event.deliveryId(),
                event.trackingNumber(),
                event.courierId(),
                event.courierName(),
                event.courierPhone(),
                event.destinationAddress()
        );
    }
}

