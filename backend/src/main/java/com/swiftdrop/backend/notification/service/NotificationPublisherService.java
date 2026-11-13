package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.CourierAssignedEvent;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationPublisherService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisherService.class);

    private final RabbitTemplate rabbitTemplate;
    private final UserDirectory userDirectory;

    @Autowired
    public NotificationPublisherService(RabbitTemplate rabbitTemplate,
                                        @Autowired(required = false) UserDirectory userDirectory) {
        this.rabbitTemplate = rabbitTemplate;
        this.userDirectory = userDirectory;
    }

    public NotificationPublisherService(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, null);
    }

    /**
     * Publishes a delivery status changed event to RabbitMQ topic exchange.
     */
    public void publishDeliveryStatusChanged(Delivery delivery, TrackingEvent event) {
        publishDeliveryStatusChanged(delivery, event, null);
    }

    /**
     * Publishes a delivery status changed event with explicit customer email.
     */
    public void publishDeliveryStatusChanged(Delivery delivery, TrackingEvent event, String customerEmail) {
        Customer customer = delivery.getCustomer();

        String customerName = "Customer";
        String customerPhone = null;

        if (customer != null) {
            if (customer.getUser() != null) {
                customerName = customer.getUser().getName().orElse("Customer");
                if ((customerEmail == null || customerEmail.isBlank()) && userDirectory != null) {
                    try {
                        customerEmail = userDirectory.email(customer.getUser().getId()).orElse(null);
                    } catch (Exception ex) {
                        log.debug("Could not resolve user email from directory: {}", ex.getMessage());
                    }
                }
            }
            customerPhone = customer.getPhoneNumber();
        }

        String destPhone = null;
        String destName = null;
        if (delivery.getDestinationAddress().isPresent()) {
            destPhone = delivery.getDestinationAddress().get().getContactPhone();
            destName = delivery.getDestinationAddress().get().getContactName();
            if (customerPhone == null || customerPhone.isBlank()) {
                customerPhone = destPhone;
            }
        }

        String courierName = delivery.getCourier() != null
                ? delivery.getCourier().getName().orElse("Courier")
                : null;

        String routingKey = "delivery.status." + delivery.getStatus().name().toLowerCase();

        Map<String, Object> metadata = new HashMap<>();
        if (destPhone != null) metadata.put("destinationPhone", destPhone);
        if (destName != null) metadata.put("destinationName", destName);

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
                metadata
        );

        try {
            log.info("Publishing DeliveryStatusChangedEvent to {} [routingKey={}, email={}, phone={}]",
                    RabbitMqConfig.EXCHANGE_SWIFTDROP, routingKey, customerEmail, customerPhone);
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_SWIFTDROP, routingKey, payload);
        } catch (Exception ex) {
            log.warn("Could not publish DeliveryStatusChangedEvent to RabbitMQ (is broker running?): {}", ex.getMessage());
        }
    }

    /**
     * Convenience helper to publish status change event given a delivery and status strings.
     */
    public void publishDeliveryStatusChanged(Delivery delivery, String status, String description, String locationName) {
        if (delivery == null) {
            return;
        }
        TrackingEvent event = new TrackingEvent(
                DeliveryStatus.valueOf(status),
                description,
                locationName,
                Instant.now(),
                null
        );
        publishDeliveryStatusChanged(delivery, event);
    }

    /**
     * Publishes courier assigned event from CourierAssignment entity.
     */
    public void publishCourierAssigned(CourierAssignment assignment) {
        if (assignment == null) {
            return;
        }
        Delivery d = assignment.getDelivery();
        Courier c = assignment.getCourier();
        String dest = (d != null && d.getDestinationAddress().isPresent())
                ? d.getDestinationAddress().get().getCity() + ", " + d.getDestinationAddress().get().getStreet()
                : "Destination";
        String courierName = (c != null && c.getUser() != null)
                ? c.getUser().getName().orElse("Courier")
                : "Courier";
        String courierPhone = c != null ? c.getPhoneNumber() : null;
        Long courierId = c != null ? c.getId() : null;

        publishCourierAssigned(
                d != null ? d.getId() : null,
                d != null ? d.getTrackingNumber() : null,
                courierId,
                courierName,
                courierPhone,
                dest
        );
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

        try {
            log.info("Publishing CourierAssignedEvent to exchange {}", RabbitMqConfig.EXCHANGE_SWIFTDROP);
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_SWIFTDROP, RabbitMqConfig.ROUTING_KEY_ASSIGNMENT, event);
        } catch (Exception ex) {
            log.warn("Could not publish CourierAssignedEvent to RabbitMQ (is broker running?): {}", ex.getMessage());
        }
    }
}
