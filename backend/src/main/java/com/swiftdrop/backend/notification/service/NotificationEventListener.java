package com.swiftdrop.backend.notification.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.entity.*;
import com.swiftdrop.backend.notification.event.CourierAssignedEvent;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import com.swiftdrop.backend.notification.event.MerchantConfirmedWebhook;
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
    private final CustomerDeliveryRepository deliveries;

    private final TrackingLinks trackingLinks;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationEventListener(EmailSender emailSender,
                                   SmsSender smsSender,
                                   TemplateRenderer templateRenderer,
                                   NotificationTemplateRepository templateRepository,
                                   NotificationLogRepository logRepository,
                                   WebhookDispatcherService webhookDispatcher,
                                   ObjectMapper objectMapper,
                                   CustomerDeliveryRepository deliveries,
                                   TrackingLinks trackingLinks) {
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.templateRenderer = templateRenderer;
        this.templateRepository = templateRepository;
        this.logRepository = logRepository;
        this.webhookDispatcher = webhookDispatcher;
        this.objectMapper = objectMapper;
        this.deliveries = deliveries;
        this.trackingLinks = trackingLinks;
    }

    /** Constructor for testing without delivery repository (links point to the local website) */
    public NotificationEventListener(EmailSender emailSender,
                                   SmsSender smsSender,
                                   TemplateRenderer templateRenderer,
                                   NotificationTemplateRepository templateRepository,
                                   NotificationLogRepository logRepository,
                                   WebhookDispatcherService webhookDispatcher,
                                   ObjectMapper objectMapper) {
        this(emailSender, smsSender, templateRenderer, templateRepository, logRepository, webhookDispatcher, objectMapper,
                null, new TrackingLinks("http://localhost:5173"));
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_DELIVERY_NOTIFICATIONS)
    @Transactional
    public void handleDeliveryStatusChanged(DeliveryStatusChangedEvent event) {
        log.info("Received DeliveryStatusChangedEvent for delivery {} [status={}]", event.deliveryId(), event.status());

        Delivery delivery = (deliveries != null && event.deliveryId() != null)
                ? deliveries.findById(event.deliveryId()).orElse(null)
                : null;

        Map<String, Object> vars = new HashMap<>();
        vars.put("trackingNumber", event.trackingNumber());
        vars.put("status", event.status());
        vars.put("description", event.description() != null ? event.description() : "");
        vars.put("locationName", event.locationName() != null ? event.locationName() : "");
        vars.put("customerName", event.customerName() != null ? event.customerName() : "Customer");
        vars.put("courierName", event.courierName() != null ? event.courierName() : "Courier");
        vars.put("trackingUrl", trackingLinks.forDelivery(event.publicTrackingToken(), event.trackingNumber()));

        // 1. Notify Customer via Email
        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            sendCustomerEmail(event, vars, delivery);
        }

        // 2. Notify Customer via SMS
        if (event.customerPhone() != null && !event.customerPhone().isBlank()) {
            sendCustomerSms(event, vars, delivery);
        }

        // 2b. Notify Recipient via SMS if destination contact phone differs from customer phone
        Object destPhoneObj = event.metadata() != null ? event.metadata().get("destinationPhone") : null;
        if (destPhoneObj instanceof String destPhone && !destPhone.isBlank() && !destPhone.equals(event.customerPhone())) {
            sendRecipientSms(event, vars, delivery, destPhone);
        }

        // 3. Trigger Merchant Webhook if associated with a merchant
        if (event.merchantId() != null && !MerchantConfirmedWebhook.STATUS.equals(event.status())) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                webhookDispatcher.dispatchWebhook(event.merchantId(), event.deliveryId(), event.status(), payload);
            } catch (JacksonException e) {
                log.error("Failed to serialize webhook payload for delivery {}", event.deliveryId(), e);
            }
        }
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_COURIER_ASSIGNMENTS)
    @Transactional
    public void handleCourierAssigned(CourierAssignedEvent event) {
        log.info("Received CourierAssignedEvent for delivery {} and courier {}", event.deliveryId(), event.courierId());

        Delivery delivery = (deliveries != null && event.deliveryId() != null)
                ? deliveries.findById(event.deliveryId()).orElse(null)
                : null;

        if (event.courierPhone() != null && !event.courierPhone().isBlank()) {
            String message = String.format("SwiftDrop: New delivery #%s assigned to you. Destination: %s",
                    event.trackingNumber(), event.destinationAddress());

            NotificationLog logEntry = new NotificationLog(
                    delivery,
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

        // Notify customer via email if available
        if (delivery != null && delivery.getCustomer() != null) {
            String customerEmail = delivery.getCustomer().getEmail();
            if (customerEmail != null && !customerEmail.isBlank()) {
                String courierName = event.courierName() != null ? event.courierName() : "A courier";
                String subject = "SwiftDrop: Courier Assigned for #" + event.trackingNumber();
                String trackingUrl = trackingLinks.forDelivery(delivery.getPublicTrackingToken(), delivery.getTrackingNumber());
                String body = String.format("""
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 8px;">
                      <h2 style="color: #0f172a; margin-top: 0;">Courier Assigned!</h2>
                      <p>Hello <b>%s</b>,</p>
                      <p>Good news! Courier <b>%s</b> has been assigned to collect your parcel <b>#%s</b>.</p>
                      <p><a href="%s" style="display: inline-block; padding: 12px 24px; background: #0f172a; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Track Parcel</a></p>
                      <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">Thank you for using SwiftDrop.</p>
                    </div>
                    """, delivery.getCustomer().getFullName() != null ? delivery.getCustomer().getFullName() : "Customer",
                        courierName, event.trackingNumber(), trackingUrl);

                NotificationLog custLog = new NotificationLog(
                        delivery,
                        customerEmail,
                        RecipientRole.CUSTOMER,
                        NotificationChannel.EMAIL,
                        "COURIER_ASSIGNED",
                        subject,
                        body
                );
                logRepository.save(custLog);
                try {
                    emailSender.send(customerEmail, subject, body);
                    custLog.markSent(Instant.now());
                } catch (Exception ex) {
                    custLog.markFailed(ex.getMessage());
                }
                logRepository.save(custLog);
            }
        }
    }

    private void sendCustomerEmail(DeliveryStatusChangedEvent event, Map<String, Object> vars, Delivery delivery) {
        String defaultSubject = resolveDefaultEmailSubject(event.status(), event.trackingNumber());
        String defaultBody = resolveDefaultEmailBody(event, vars);

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
                delivery,
                event.customerEmail(),
                RecipientRole.CUSTOMER,
                NotificationChannel.EMAIL,
                event.status(),
                subject,
                body
        );
        logRepository.save(logEntry);

        try {
            log.info("Dispatching notification email for delivery {} to {}", event.deliveryId(), event.customerEmail());
            emailSender.send(event.customerEmail(), subject, body);
            logEntry.markSent(Instant.now());
            log.info("Successfully sent notification email to {}", event.customerEmail());
        } catch (Exception ex) {
            log.error("Failed to send notification email to {}: {}", event.customerEmail(), ex.getMessage(), ex);
            logEntry.markFailed(ex.getMessage());
        }
        logRepository.save(logEntry);
    }

    private void sendCustomerSms(DeliveryStatusChangedEvent event, Map<String, Object> vars, Delivery delivery) {
        String defaultSms = resolveDefaultCustomerSms(event, vars);

        NotificationTemplate template = templateRepository
                .findByEventTypeAndChannelAndActiveTrue(event.status(), NotificationChannel.SMS)
                .orElse(null);

        String message = template != null
                ? templateRenderer.render(template.getBodyTemplate(), vars)
                : defaultSms;

        NotificationLog logEntry = new NotificationLog(
                delivery,
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

    private void sendRecipientSms(DeliveryStatusChangedEvent event, Map<String, Object> vars, Delivery delivery, String recipientPhone) {
        String defaultSms = resolveDefaultRecipientSms(event, vars);

        NotificationTemplate template = templateRepository
                .findByEventTypeAndChannelAndActiveTrue(event.status(), NotificationChannel.SMS)
                .orElse(null);

        String message = template != null
                ? templateRenderer.render(template.getBodyTemplate(), vars)
                : defaultSms;

        NotificationLog logEntry = new NotificationLog(
                delivery,
                recipientPhone,
                RecipientRole.CUSTOMER,
                NotificationChannel.SMS,
                event.status(),
                null,
                message
        );
        logRepository.save(logEntry);

        try {
            smsSender.send(recipientPhone, message);
            logEntry.markSent(Instant.now());
        } catch (Exception ex) {
            logEntry.markFailed(ex.getMessage());
        }
        logRepository.save(logEntry);
    }

    private String resolveDefaultEmailSubject(String status, String trackingNumber) {
        if (status == null) return "SwiftDrop Delivery Update: #" + trackingNumber;
        return switch (status.toUpperCase()) {
            case "CONFIRMED" -> "SwiftDrop: Booking Confirmed for #" + trackingNumber;
            case "PICKED_UP" -> "SwiftDrop: Parcel #" + trackingNumber + " has been Picked Up";
            case "IN_TRANSIT" -> "SwiftDrop: Parcel #" + trackingNumber + " is in Transit";
            case "OUT_FOR_DELIVERY" -> "SwiftDrop: Parcel #" + trackingNumber + " is Out for Delivery!";
            case "DELIVERED" -> "SwiftDrop: Parcel #" + trackingNumber + " Delivered Successfully";
            case "FAILED" -> "SwiftDrop: Delivery Attempt Failed for #" + trackingNumber;
            case "CANCELLED" -> "SwiftDrop: Booking Cancelled for #" + trackingNumber;
            default -> "SwiftDrop Delivery Update: #" + trackingNumber + " is " + status;
        };
    }

    private String resolveDefaultEmailBody(DeliveryStatusChangedEvent event, Map<String, Object> vars) {
        String status = event.status() != null ? event.status().toUpperCase() : "UPDATED";
        String customerName = java.util.Objects.toString(vars.get("customerName"), "Customer");
        String trackingNumber = event.trackingNumber();
        String trackingUrl = java.util.Objects.toString(vars.get("trackingUrl"), "#");
        String courierName = java.util.Objects.toString(vars.get("courierName"), "our courier");
        String description = java.util.Objects.toString(vars.get("description"), "");

        String headline;
        String message;
        String color = "#0f172a";

        switch (status) {
            case "CONFIRMED" -> {
                headline = "Booking Confirmed!";
                message = "Your delivery booking has been successfully confirmed. We are assigning a courier to collect your parcel.";
            }
            case "PICKED_UP" -> {
                headline = "Parcel Picked Up!";
                message = "Our courier <b>" + courierName + "</b> has picked up your parcel and it is on the way to our sorting hub.";
            }
            case "IN_TRANSIT" -> {
                headline = "In Transit";
                message = "Your parcel <b>#" + trackingNumber + "</b> is currently on the move"
                        + (!description.isBlank() ? ": " + description : ".");
            }
            case "OUT_FOR_DELIVERY" -> {
                headline = "Out for Delivery Today!";
                color = "#2563eb";
                message = "Great news! Courier <b>" + courierName + "</b> is out for delivery with your parcel <b>#"
                        + trackingNumber + "</b>. Please be available at the destination address to receive it.";
            }
            case "DELIVERED" -> {
                headline = "Package Delivered!";
                color = "#16a34a";
                message = "Your parcel <b>#" + trackingNumber + "</b> has been successfully delivered. Thank you for choosing SwiftDrop!";
            }
            case "FAILED" -> {
                headline = "Delivery Attempt Failed";
                color = "#dc2626";
                message = "We attempted to deliver your parcel <b>#" + trackingNumber + "</b>, but were unable to complete the delivery"
                        + (!description.isBlank() ? " (" + description + ")" : "") + ". Our team will attempt redelivery.";
            }
            case "CANCELLED" -> {
                headline = "Booking Cancelled";
                color = "#64748b";
                message = "Your delivery booking <b>#" + trackingNumber + "</b> has been cancelled.";
            }
            default -> {
                headline = "Delivery Status: " + status;
                message = "Your parcel status has been updated to: <b>" + status + "</b>.";
            }
        }

        return String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 8px;">
              <h2 style="color: %s; margin-top: 0;">%s</h2>
              <p>Hello <b>%s</b>,</p>
              <p>%s</p>
              <div style="background: #f8fafc; padding: 16px; border-radius: 6px; margin: 20px 0;">
                <p style="margin: 0; color: #64748b; font-size: 13px;">Tracking Number</p>
                <p style="margin: 4px 0 0; font-size: 18px; font-weight: bold; color: #0f172a;">%s</p>
              </div>
              <p><a href="%s" style="display: inline-block; padding: 12px 24px; background: %s; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Track Live on SwiftDrop</a></p>
              <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">Thank you for using SwiftDrop Courier Service.</p>
            </div>
            """, color, headline, customerName, message, trackingNumber, trackingUrl, color);
    }

    private String resolveDefaultCustomerSms(DeliveryStatusChangedEvent event, Map<String, Object> vars) {
        String status = event.status() != null ? event.status().toUpperCase() : "UPDATED";
        String trackingNumber = event.trackingNumber();
        String trackingUrl = java.util.Objects.toString(vars.get("trackingUrl"), "");
        String courierName = java.util.Objects.toString(vars.get("courierName"), "courier");
        String description = java.util.Objects.toString(vars.get("description"), "");

        return switch (status) {
            case "CONFIRMED" -> "SwiftDrop: Booking confirmed for parcel #" + trackingNumber + ". Track live at " + trackingUrl;
            case "PICKED_UP" -> "SwiftDrop: Parcel #" + trackingNumber + " picked up by " + courierName + ". Track: " + trackingUrl;
            case "IN_TRANSIT" -> "SwiftDrop: Parcel #" + trackingNumber + " is in transit" + (!description.isBlank() ? " (" + description + ")" : "") + ". Track: " + trackingUrl;
            case "OUT_FOR_DELIVERY" -> "SwiftDrop: Parcel #" + trackingNumber + " is OUT FOR DELIVERY with " + courierName + ". Track: " + trackingUrl;
            case "DELIVERED" -> "SwiftDrop: Parcel #" + trackingNumber + " was successfully delivered. Thank you for choosing SwiftDrop!";
            case "FAILED" -> "SwiftDrop: Delivery attempt failed for parcel #" + trackingNumber + (!description.isBlank() ? " (" + description + ")" : "") + ". Track: " + trackingUrl;
            case "CANCELLED" -> "SwiftDrop: Booking #" + trackingNumber + " has been cancelled.";
            default -> "SwiftDrop: Parcel #" + trackingNumber + " is now " + status + ". Track: " + trackingUrl;
        };
    }

    private String resolveDefaultRecipientSms(DeliveryStatusChangedEvent event, Map<String, Object> vars) {
        String status = event.status() != null ? event.status().toUpperCase() : "UPDATED";
        String trackingNumber = event.trackingNumber();
        String trackingUrl = java.util.Objects.toString(vars.get("trackingUrl"), "");
        String customerName = java.util.Objects.toString(vars.get("customerName"), "the sender");
        String courierName = java.util.Objects.toString(vars.get("courierName"), "our courier");

        return switch (status) {
            case "CONFIRMED" -> "SwiftDrop: A parcel #" + trackingNumber + " is on its way to you from " + customerName + "! Track live: " + trackingUrl;
            case "OUT_FOR_DELIVERY" -> "SwiftDrop: Parcel #" + trackingNumber + " is out for delivery to you today with courier " + courierName + ". Track: " + trackingUrl;
            case "DELIVERED" -> "SwiftDrop: Parcel #" + trackingNumber + " has been delivered to your address! Enjoy your package.";
            case "FAILED" -> "SwiftDrop: Delivery attempt failed for parcel #" + trackingNumber + ". We will re-attempt delivery shortly. Track: " + trackingUrl;
            default -> "SwiftDrop: Parcel #" + trackingNumber + " update: " + status + ". Track: " + trackingUrl;
        };
    }
}
