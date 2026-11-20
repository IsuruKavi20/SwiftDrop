package com.swiftdrop.backend.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import com.swiftdrop.backend.notification.event.MerchantAssignmentWebhook;
import com.swiftdrop.backend.notification.event.MerchantConfirmedWebhook;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Focused regression test for RabbitMQ message serialization and deserialization.
 * Verifies that DeliveryStatusChangedEvent with Java 8 time (Instant) and merchant metadata
 * converts cleanly to and from AMQP Message JSON without requiring a live RabbitMQ broker.
 */
class NotificationMessageConverterTest {

    private final RabbitMqConfig config = new RabbitMqConfig();
    private final ObjectMapper objectMapper = config.objectMapper();
    private final MessageConverter messageConverter = config.jsonMessageConverter(objectMapper);

    @Test
    void convertsMerchantDeliveryStatusChangedEventWithoutBroker() {
        Instant timestamp = Instant.parse("2026-11-19T08:30:00Z");
        DeliveryStatusChangedEvent event = new DeliveryStatusChangedEvent(
                101L,
                "SD-MERCHANT-001",
                "public-token-abc",
                "PENDING",
                "Delivery booked by merchant",
                null,
                "Ceylon Tea Exports",
                "orders@ceylontea.lk",
                "0112345678",
                null,
                null,
                42L,
                timestamp,
                Map.of("channel", "MERCHANT_PORTAL")
        );

        // 1. Serialization into AMQP Message using Jackson2JsonMessageConverter
        Message message = assertDoesNotThrow(() ->
                messageConverter.toMessage(event, new MessageProperties())
        );

        assertThat(message).isNotNull();
        String json = new String(message.getBody(), StandardCharsets.UTF_8);

        assertThat(json).contains("\"deliveryId\":101");
        assertThat(json).contains("\"trackingNumber\":\"SD-MERCHANT-001\"");
        assertThat(json).contains("\"status\":\"PENDING\"");
        assertThat(json).contains("\"merchantId\":42");
        assertThat(json).contains("\"timestamp\":\"2026-11-19T08:30:00Z\"");

        // 2. Deserialization from AMQP Message back to DeliveryStatusChangedEvent
        Object roundTrip = messageConverter.fromMessage(message);
        assertThat(roundTrip).isInstanceOf(DeliveryStatusChangedEvent.class);

        DeliveryStatusChangedEvent deserialized = (DeliveryStatusChangedEvent) roundTrip;
        assertThat(deserialized.deliveryId()).isEqualTo(101L);
        assertThat(deserialized.trackingNumber()).isEqualTo("SD-MERCHANT-001");
        assertThat(deserialized.merchantId()).isEqualTo(42L);
        assertThat(deserialized.timestamp()).isEqualTo(timestamp);
        assertThat(deserialized.customerName()).isEqualTo("Ceylon Tea Exports");

        // 3. Webhook payload serialization via ObjectMapper (used by NotificationEventListener)
        String webhookPayload = assertDoesNotThrow(() ->
                objectMapper.writeValueAsString(event)
        );
        assertThat(webhookPayload).contains("\"merchantId\":42");
        assertThat(webhookPayload).contains("\"timestamp\":\"2026-11-19T08:30:00Z\"");
    }

    @Test
    void merchantQueueCanDeserializeBothEventTypesWithoutAnInferredType() {
        var timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var confirmation = new MerchantConfirmedWebhook(42L,
                new MerchantConfirmedWebhook.Payload("delivery-status-1", 5L, "SD-123", "CONFIRMED", timestamp));
        var assignment = new MerchantAssignmentWebhook(42L,
                new MerchantAssignmentWebhook.Payload("courier-assignment-2", 5L, "SD-123", "COURIER_ASSIGNED", timestamp));
        for (Object value : new Object[]{confirmation, assignment}) {
            var message = messageConverter.toMessage(value, new MessageProperties());
            assertThat(messageConverter.fromMessage(message)).isEqualTo(value);
        }
    }

    @Test
    void rejectsUntrustedTypeHeader() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "com.example.untrusted.ExploitPayload");

        Message untrustedMessage = new Message("{\"some\":\"payload\"}".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> messageConverter.fromMessage(untrustedMessage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the trusted packages");
    }
}

