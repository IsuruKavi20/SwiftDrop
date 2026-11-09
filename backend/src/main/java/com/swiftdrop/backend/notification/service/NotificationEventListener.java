package com.swiftdrop.backend.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.entity.*;
import com.swiftdrop.backend.notification.event.CourierAssignedEvent;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import com.swiftdrop.backend.notification.repository.NotificationLogRepository;
import com.swiftdrop.backend.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final TemplateRenderer templateRenderer;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;
    private final WebhookDispatcherService webhookDispatcher;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(EmailSender emailSender,
                                   SmsSender smsSender,
                                   TemplateRenderer templateRenderer,
                                   NotificationTemplateRepository templateRepository,
                                   NotificationLogRepository logRepository,
                                   WebhookDispatcherService webhookDispatcher,
                                   ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.templateRenderer = templateRenderer;
        this.templateRepository = templateRepository;
        this.logRepository = logRepository;
        this.webhookDispatcher = webhookDispatcher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_DELIVERY_NOTIFICATIONS)
    @Transactional
    public void handleDeliveryStatusChanged(DeliveryStatusChangedEvent event) {
        log.info("Received DeliveryStatusChangedEvent for delivery {} [status={}]", event.deliveryId(), event.status());

        Map<String, Object> vars = new HashMap<>();
        vars.put("trackingNumber", event.trackingNumber());
        vars.put("status", event.status());
        vars.put("description", event.description() != null ? event.description() : "");
        vars.put("locationName", event.locationName() != null ? event.locationName() : "");
        vars.put("customerName", event.customerName() != null ? event.customerName() : "Customer");
        vars.put("courierName", event.courierName() != null ? event.courierName() : "Courier");
        vars.put("trackingUrl", "http://localhost:5173/track/" + (event.publicTrackingToken() != null ? event.publicTrackingToken() : event.trackingNumber()));

        // 1. Notify Customer via Email
        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            sendCustomerEmail(event, vars);
        }

        // 2. Notify Customer via SMS
        if (event.customerPhone() != null && !event.customerPhone().isBlank()) {
            sendCustomerSms(event, vars);
        }

        // 3. Trigger Merchant Webhook if associated with a merchant
        if (event.merchantId() != null) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                webhookDispatcher.dispatchWebhook(event.merchantId(), event.deliveryId(), event.status(), payload);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize webhook payload for delivery {}", event.deliveryId(), e);
            }
        }
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_DELIVERY_NOTIFICATIONS)
    @Transactional
    public void handleCourierAssigned(CourierAssignedEvent event) {
        log.info("Received CourierAssignedEvent for delivery {} and courier {}", event.deliveryId(), event.courierId());

        if (event.courierPhone() != null && !event.courierPhone().isBlank()) {
            String message = String.format("SwiftDrop: New delivery #%s assigned to you. Destination: %s",
                    event.trackingNumber(), event.destinationAddress());

            NotificationLog logEntry = new NotificationLog(
                    null,
                    event.courierPhone(),
                    RecipientRole.COURIER,
                    NotificationChannel.SMS,
                    "COURIER_ASSIGNED",
                    null,
                    message
            );
            logRepository.save(logEntry);

            try {
                smsSender.send(event.courierPhone(), message);
                logEntry.markSent(Instant.now());
            } catch (Exception ex) {
                logEntry.markFailed(ex.getMessage());
            }
            logRepository.save(logEntry);
        }
    }

    private void sendCustomerEmail(DeliveryStatusChangedEvent event, Map<String, Object> vars) {
        String defaultSubject = "SwiftDrop Delivery Update: " + event.trackingNumber() + " is " + event.status();
        String defaultBody = "<p>Hello <b>" + vars.get("customerName") + "</b>,</p>"
                + "<p>Your parcel status has been updated to: <b>" + event.status() + "</b>.</p>"
                + "<p><a href=\"" + vars.get("trackingUrl") + "\">Click here to track your parcel</a></p>";

        NotificationTemplate template = templateRepository
                .findByEventTypeAndChannelAndActiveTrue(event.status(), NotificationChannel.EMAIL)
                .orElse(null);

        String subject = template != null && template.getSubjectTemplate() != null
                ? templateRenderer.render(template.getSubjectTemplate(), vars)
                : defaultSubject;

        String body = template != null
                ? templateRenderer.render(template.getBodyTemplate(), vars)
                : defaultBody;

        NotificationLog logEntry = new NotificationLog(
                null,
                event.customerEmail(),
                RecipientRole.CUSTOMER,
                NotificationChannel.EMAIL,
                event.status(),
                subject,
                body
        );
        logRepository.save(logEntry);

        try {
            emailSender.send(event.customerEmail(), subject, body);
            logEntry.markSent(Instant.now());
        } catch (Exception ex) {
            logEntry.markFailed(ex.getMessage());
        }
        logRepository.save(logEntry);
    }

    private void sendCustomerSms(DeliveryStatusChangedEvent event, Map<String, Object> vars) {
        String defaultSms = "SwiftDrop: Parcel " + event.trackingNumber() + " is now " + event.status()
                + ". Track: " + vars.get("trackingUrl");

        NotificationTemplate template = templateRepository
                .findByEventTypeAndChannelAndActiveTrue(event.status(), NotificationChannel.SMS)
                .orElse(null);

        String message = template != null
                ? templateRenderer.render(template.getBodyTemplate(), vars)
                : defaultSms;

        NotificationLog logEntry = new NotificationLog(
                null,
                event.customerPhone(),
                RecipientRole.CUSTOMER,
                NotificationChannel.SMS,
                event.status(),
                null,
                message
        );
        logRepository.save(logEntry);

        try {
            smsSender.send(event.customerPhone(), message);
            logEntry.markSent(Instant.now());
        } catch (Exception ex) {
            logEntry.markFailed(ex.getMessage());
        }
        logRepository.save(logEntry);
    }
}

