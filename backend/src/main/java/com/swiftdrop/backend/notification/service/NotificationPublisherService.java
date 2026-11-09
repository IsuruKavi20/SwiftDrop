package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.CourierAssignedEvent;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class NotificationPublisherService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisherService.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a delivery status changed event to RabbitMQ topic exchange.
     */
    public void publishDeliveryStatusChanged(Delivery delivery, TrackingEvent event) {
        Customer customer = delivery.getCustomer();

        String customerName = "Customer";
        String customerEmail = null;
        String customerPhone = null;

        if (customer != null) {
            if (customer.getUser() != null) {
                customerName = customer.getUser().getName().orElse("Customer");
            }
            customerPhone = customer.getPhoneNumber();
        }

        // If recipient address has a contact name and phone, that can also be used
        delivery.getDestinationAddress().ifPresent(dest -> {
            if (dest.getContactPhone() != null && !dest.getContactPhone().isBlank()) {
                // destination contact phone available
            }
        });

        String courierName = delivery.getCourier() != null
                ? delivery.getCourier().getName().orElse("Courier")
                : null;

        String routingKey = "delivery.status." + delivery.getStatus().name().toLowerCase();

        DeliveryStatusChangedEvent payload = new DeliveryStatusChangedEvent(
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getPublicTrackingToken(),
                delivery.getStatus().name(),
                event != null ? event.getDescription() : null,
                event != null ? event.getLocationName() : null,
                customerName,
                customerEmail,
                customerPhone,
                courierName,
                null,
                null, // merchantId if applicable
                event != null ? event.getEventTime() : Instant.now(),
                Map.of()
        );

        log.info("Publishing DeliveryStatusChangedEvent to {} [routingKey={}]", RabbitMqConfig.EXCHANGE_SWIFTDROP, routingKey);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_SWIFTDROP, routingKey, payload);
    }

    /**
     * Publishes courier assigned event.
     */
    public void publishCourierAssigned(Long deliveryId, String trackingNumber, Long courierId,
                                       String courierName, String courierPhone, String destinationAddress) {
        CourierAssignedEvent event = new CourierAssignedEvent(
                deliveryId,
                trackingNumber,
                courierId,
                courierName,
                courierPhone,
                null,
                null,
                null,
                destinationAddress,
                Instant.now()
        );

        log.info("Publishing CourierAssignedEvent to exchange {}", RabbitMqConfig.EXCHANGE_SWIFTDROP);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_SWIFTDROP, RabbitMqConfig.ROUTING_KEY_ASSIGNMENT, event);
    }
}
