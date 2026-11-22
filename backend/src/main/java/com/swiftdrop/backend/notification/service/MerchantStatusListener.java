package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.courier.event.MerchantCourierStatusChanged;
import com.swiftdrop.backend.notification.event.MerchantLifecycleWebhook;
import com.swiftdrop.backend.dispatcher.event.MerchantCourierAssigned;
import com.swiftdrop.backend.notification.event.MerchantAssignmentWebhook;
import com.swiftdrop.backend.merchant.event.MerchantDeliveryConfirmed;
import com.swiftdrop.backend.merchant.event.MerchantStatusChange;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.MerchantConfirmedWebhook;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.util.concurrent.TimeUnit;
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

    private final RabbitTemplate rabbit;

    MerchantStatusListener(EntityManager entities, NotificationPublisherService publisher, RabbitTemplate rabbit) {
        this.entities = entities;
        this.publisher = publisher;
        this.rabbit = rabbit;
    }

    @ApplicationModuleListener
    void publishConfirmationWebhook(MerchantDeliveryConfirmed event) throws Exception {
        var payload = new MerchantConfirmedWebhook.Payload("delivery-status-" + event.trackingEventId(),
                event.deliveryId(), event.trackingNumber(), MerchantConfirmedWebhook.STATUS, event.timestamp());
        publishWebhook(MerchantConfirmedWebhook.ROUTING_KEY, new MerchantConfirmedWebhook(event.merchantId(), payload));
    }

    @ApplicationModuleListener
    void publishAssignmentWebhook(MerchantCourierAssigned event) throws Exception {
        var payload = new MerchantAssignmentWebhook.Payload("courier-assignment-" + event.assignmentId(),
                event.deliveryId(), event.trackingNumber(), MerchantAssignmentWebhook.EVENT_TYPE, event.timestamp());
        publishWebhook(MerchantAssignmentWebhook.ROUTING_KEY, new MerchantAssignmentWebhook(event.merchantId(), payload));
    }

    @ApplicationModuleListener
    void publishLifecycleWebhook(MerchantCourierStatusChanged event) throws Exception {
        var payload = new MerchantLifecycleWebhook.Payload("delivery-status-" + event.trackingEventId(),
                event.deliveryId(), event.trackingNumber(), event.status().name(), event.timestamp());
        publishWebhook(MerchantLifecycleWebhook.ROUTING_KEY, new MerchantLifecycleWebhook(event.merchantId(), payload));
    }

    private void publishWebhook(String routingKey, Object message) throws Exception {
        var correlation = new CorrelationData();
        rabbit.convertAndSend(RabbitMqConfig.EXCHANGE_SWIFTDROP, routingKey, message, correlation);
        if (!correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("Merchant webhook was not accepted by the broker");
        }
    }

    @ApplicationModuleListener
    void publishConfirmationNotification(MerchantDeliveryConfirmed event) {
        on(new MerchantStatusChange(event.deliveryId(), event.trackingEventId()));
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
