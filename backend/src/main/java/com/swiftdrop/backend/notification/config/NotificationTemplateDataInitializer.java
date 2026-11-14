package com.swiftdrop.backend.notification.config;

import com.swiftdrop.backend.notification.entity.NotificationChannel;
import com.swiftdrop.backend.notification.entity.NotificationTemplate;
import com.swiftdrop.backend.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds default email and SMS notification templates into notification_template table on startup
 * if they do not already exist.
 */
@Component
public class NotificationTemplateDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateDataInitializer.class);

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateDataInitializer(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public void run(String... args) {
        seedTemplates();
    }

    public void seedTemplates() {
        List<NotificationTemplate> defaultTemplates = List.of(
                // 1. CONFIRMED
                new NotificationTemplate(
                        "CONFIRMED",
                        NotificationChannel.EMAIL,
                        "SwiftDrop: Booking Confirmed for #{{trackingNumber}}",
                        """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                          <h2 style="color: #0f172a;">Booking Confirmed!</h2>
                          <p>Hello <b>{{customerName}}</b>,</p>
                          <p>Your delivery booking has been successfully confirmed. We are assigning a courier to collect your parcel.</p>
                          <div style="background: #f8fafc; padding: 16px; border-radius: 6px; margin: 20px 0;">
                            <p style="margin: 0; color: #64748b; font-size: 13px;">Tracking Number</p>
                            <p style="margin: 4px 0 0; font-size: 18px; font-weight: bold; color: #0f172a;">{{trackingNumber}}</p>
                          </div>
                          <p><a href="{{trackingUrl}}" style="display: inline-block; padding: 10px 20px; background: #0f172a; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Track Your Parcel</a></p>
                          <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">Thank you for choosing SwiftDrop Courier Service.</p>
                        </div>
                        """
                ),
                new NotificationTemplate(
                        "CONFIRMED",
                        NotificationChannel.SMS,
                        null,
                        "SwiftDrop: Booking confirmed for parcel #{{trackingNumber}}. Track live at {{trackingUrl}}"
                ),

                // 2. PICKED_UP
                new NotificationTemplate(
                        "PICKED_UP",
                        NotificationChannel.EMAIL,
                        "SwiftDrop: Parcel #{{trackingNumber}} has been Picked Up",
                        """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                          <h2 style="color: #0f172a;">Parcel Picked Up!</h2>
                          <p>Hello <b>{{customerName}}</b>,</p>
                          <p>Our courier <b>{{courierName}}</b> has picked up your parcel and it is on the way to our sorting hub.</p>
                          <p><a href="{{trackingUrl}}" style="display: inline-block; padding: 10px 20px; background: #0f172a; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Track Progress</a></p>
                          <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">SwiftDrop Deliveries</p>
                        </div>
                        """
                ),
                new NotificationTemplate(
                        "PICKED_UP",
                        NotificationChannel.SMS,
                        null,
                        "SwiftDrop: Parcel #{{trackingNumber}} picked up by {{courierName}}. Track: {{trackingUrl}}"
                ),

                // 3. IN_TRANSIT
                new NotificationTemplate(
                        "IN_TRANSIT",
                        NotificationChannel.EMAIL,
                        "SwiftDrop: Parcel #{{trackingNumber}} is in Transit",
                        """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                          <h2 style="color: #0f172a;">In Transit</h2>
                          <p>Hello <b>{{customerName}}</b>,</p>
                          <p>Your parcel <b>#{{trackingNumber}}</b> is currently on the move: {{description}}.</p>
                          <p><a href="{{trackingUrl}}" style="display: inline-block; padding: 10px 20px; background: #0f172a; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Live Tracking</a></p>
                          <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">SwiftDrop Deliveries</p>
                        </div>
                        """
                ),
                new NotificationTemplate(
                        "IN_TRANSIT",
                        NotificationChannel.SMS,
                        null,
                        "SwiftDrop: Parcel #{{trackingNumber}} is in transit. Current status: {{description}}. Track: {{trackingUrl}}"
                ),

                // 4. OUT_FOR_DELIVERY
                new NotificationTemplate(
                        "OUT_FOR_DELIVERY",
                        NotificationChannel.EMAIL,
                        "SwiftDrop: Parcel #{{trackingNumber}} is Out for Delivery!",
                        """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                          <h2 style="color: #2563eb;">Out for Delivery Today!</h2>
                          <p>Hello <b>{{customerName}}</b>,</p>
                          <p>Great news! Courier <b>{{courierName}}</b> is out for delivery with your parcel <b>#{{trackingNumber}}</b>. Please be available at the destination address to receive it.</p>
                          <p><a href="{{trackingUrl}}" style="display: inline-block; padding: 10px 20px; background: #2563eb; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Track Courier Location</a></p>
                          <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">SwiftDrop Deliveries</p>
                        </div>
                        """
                ),
                new NotificationTemplate(
                        "OUT_FOR_DELIVERY",
                        NotificationChannel.SMS,
                        null,
                        "SwiftDrop: Parcel #{{trackingNumber}} is OUT FOR DELIVERY with {{courierName}}. Track: {{trackingUrl}}"
                ),

                // 5. DELIVERED
                new NotificationTemplate(
                        "DELIVERED",
                        NotificationChannel.EMAIL,
                        "SwiftDrop: Parcel #{{trackingNumber}} Delivered Successfully",
                        """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                          <h2 style="color: #16a34a;">Package Delivered!</h2>
                          <p>Hello <b>{{customerName}}</b>,</p>
                          <p>Your parcel <b>#{{trackingNumber}}</b> has been successfully delivered. We hope you enjoyed our service!</p>
                          <div style="background: #f0fdf4; border: 1px solid #bbf7d0; padding: 14px; border-radius: 6px; margin: 20px 0;">
                            <p style="margin: 0; color: #166534; font-weight: bold;">Status: DELIVERED</p>
                          </div>
                          <p><a href="{{trackingUrl}}" style="display: inline-block; padding: 10px 20px; background: #16a34a; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">View Delivery Receipt</a></p>
                          <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">Thank you for using SwiftDrop.</p>
                        </div>
                        """
                ),
                new NotificationTemplate(
                        "DELIVERED",
                        NotificationChannel.SMS,
                        null,
                        "SwiftDrop: Parcel #{{trackingNumber}} was successfully delivered. Thank you for choosing SwiftDrop!"
                ),

                // 6. FAILED
                new NotificationTemplate(
                        "FAILED",
                        NotificationChannel.EMAIL,
                        "SwiftDrop: Delivery Attempt Failed for #{{trackingNumber}}",
                        """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                          <h2 style="color: #dc2626;">Delivery Attempt Failed</h2>
                          <p>Hello <b>{{customerName}}</b>,</p>
                          <p>We attempted to deliver your parcel <b>#{{trackingNumber}}</b>, but were unable to complete the delivery: {{description}}.</p>
                          <p>Our courier will attempt redelivery on the next business day.</p>
                          <p><a href="{{trackingUrl}}" style="display: inline-block; padding: 10px 20px; background: #dc2626; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">Check Parcel Status</a></p>
                          <p style="color: #94a3b8; font-size: 12px; margin-top: 30px;">SwiftDrop Support</p>
                        </div>
                        """
                ),
                new NotificationTemplate(
                        "FAILED",
                        NotificationChannel.SMS,
                        null,
                        "SwiftDrop: Delivery attempt failed for parcel #{{trackingNumber}}. Reason: {{description}}. Track: {{trackingUrl}}"
                )
        );

        for (NotificationTemplate template : defaultTemplates) {
            boolean exists = templateRepository
                    .findByEventTypeAndChannelAndActiveTrue(template.getEventType(), template.getChannel())
                    .isPresent();

            if (!exists) {
                templateRepository.save(template);
                log.info("Seeded notification template: event={}, channel={}", template.getEventType(), template.getChannel());
            }
        }
    }
}

