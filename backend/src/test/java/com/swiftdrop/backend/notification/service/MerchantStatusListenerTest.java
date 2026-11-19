package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.merchant.event.MerchantStatusChange;
import jakarta.persistence.EntityManager;
import com.swiftdrop.backend.dispatcher.event.MerchantCourierAssigned;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.MerchantAssignmentWebhook;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Merchant's status event reaches the notification publisher, without needing a broker in tests. */
@ExtendWith(MockitoExtension.class)
class MerchantStatusListenerTest {

    @Mock EntityManager entities;
    @Mock NotificationPublisherService publisher;
    @Mock RabbitTemplate rabbit;
    @InjectMocks MerchantStatusListener listener;

    @Test
    void repeatedPublicationOfOneAssignmentKeepsItsEventIdentity() throws Exception {
        Instant assignedAt = Instant.parse("2026-01-01T00:00:00Z");
        var event = new MerchantCourierAssigned(7L, 42L, 5L, "SD-123", assignedAt);
        var message = new MerchantAssignmentWebhook(42L, new MerchantAssignmentWebhook.Payload(
                "courier-assignment-7", 5L, "SD-123", "COURIER_ASSIGNED", assignedAt));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantAssignmentWebhook.ROUTING_KEY), eq(message), any(CorrelationData.class));

        listener.publishAssignmentWebhook(event);
        listener.publishAssignmentWebhook(event);

        verify(rabbit, times(2)).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantAssignmentWebhook.ROUTING_KEY), eq(message), any(CorrelationData.class));
    }

    @Test
    void publishesTheExactSavedTrackingEvent() {
        Delivery delivery = mock(Delivery.class);
        TrackingEvent older = mock(TrackingEvent.class);
        TrackingEvent newer = mock(TrackingEvent.class);
        when(older.getId()).thenReturn(1L);
        when(newer.getId()).thenReturn(2L);
        when(delivery.getTrackingEvents()).thenReturn(List.of(older, newer));
        when(entities.find(Delivery.class, 42L)).thenReturn(delivery);

        listener.on(new MerchantStatusChange(42L, 2L));

        verify(publisher).publishDeliveryStatusChanged(delivery, newer);
    }

    @Test
    void aDeletedDeliveryIsSkipped() {
        listener.on(new MerchantStatusChange(42L, 2L));

        verifyNoInteractions(publisher);
    }

    @Test
    void aBrokerFailureDoesNotReachTheCaller() {
        Delivery delivery = mock(Delivery.class);
        TrackingEvent event = mock(TrackingEvent.class);
        when(delivery.getTrackingEvents()).thenReturn(List.of(event));
        when(event.getId()).thenReturn(2L);
        when(entities.find(Delivery.class, 42L)).thenReturn(delivery);
        doThrow(new IllegalStateException("Broker unavailable")).when(publisher).publishDeliveryStatusChanged(delivery, event);

        assertDoesNotThrow(() -> listener.on(new MerchantStatusChange(42L, 2L)));
    }
}
