package com.swiftdrop.backend.notification;

import tools.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;
import com.swiftdrop.backend.notification.entity.NotificationChannel;
import com.swiftdrop.backend.notification.entity.NotificationTemplate;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import com.swiftdrop.backend.notification.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.backend.notification.repository.NotificationLogRepository;
import com.swiftdrop.backend.notification.repository.NotificationTemplateRepository;
import com.swiftdrop.backend.notification.repository.WebhookDeliveryLogRepository;
import com.swiftdrop.backend.notification.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSystemTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private SmsSender smsSender;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private WebhookDispatcherService webhookDispatcher;

    private TemplateRenderer templateRenderer;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        templateRenderer = new TemplateRenderer();
        listener = new NotificationEventListener(
                emailSender,
                smsSender,
                templateRenderer,
                templateRepository,
                logRepository,
                webhookDispatcher,
                new ObjectMapper()
        );
    }

    @Test
    void templateRendererReplacesPlaceholders() {
        String template = "Hello {{customerName}}, parcel {{trackingNumber}} is {{status}}.";
        Map<String, Object> vars = Map.of(
                "customerName", "Kasun",
                "trackingNumber", "SD-10023",
                "status", "OUT_FOR_DELIVERY"
        );

        String result = templateRenderer.render(template, vars);
        org.junit.jupiter.api.Assertions.assertEquals("Hello Kasun, parcel SD-10023 is OUT_FOR_DELIVERY.", result);
    }

    @Test
    void hmacSignatureCalculationProducesValidHash() {
        String secret = "secretKey123";
        String payload = "{\"deliveryId\":10,\"status\":\"DELIVERED\"}";

        String signature = WebhookDispatcherService.computeHmacSha256(payload, secret);
        assertNotNull(signature);
        assertTrue(signature.length() > 0);
    }

    @Test
    void statusChangeEventTriggersEmailAndSms() {
        DeliveryStatusChangedEvent event = new DeliveryStatusChangedEvent(
                1L,
                "SD-12345",
                "token-abc",
                "OUT_FOR_DELIVERY",
                "Parcel out for delivery",
                "Colombo Hub",
                "Kamal Silva",
                "kamal@example.com",
                "+94771234567",
                "Sunil Courier",
                null,
                null,
                Instant.now(),
                Map.of()
        );

        when(templateRepository.findByEventTypeAndChannelAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        listener.handleDeliveryStatusChanged(event);

        verify(emailSender, times(1)).send(eq("kamal@example.com"), anyString(), anyString());
        verify(smsSender, times(1)).send(eq("+94771234567"), anyString());
        verify(logRepository, atLeast(2)).save(any());
    }
}

