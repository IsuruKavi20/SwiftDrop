package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.notification.service.NotificationPublisherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
 import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/** Customer status events reuse the team's notification publisher without requiring a broker in tests. */
@ExtendWith(MockitoExtension.class)
class CustomerStatusNotificationBridgeTest {
    @Mock CustomerDeliveryRepository deliveries;
    @Mock NotificationPublisherService publisher;
    @InjectMocks CustomerStatusNotificationBridge bridge;

    @Test
    void publishesExactPersistedTrackingEvent() {
        Delivery delivery = mock(Delivery.class);
        TrackingEvent older = mock(TrackingEvent.class);
        TrackingEvent newer = mock(TrackingEvent.class);
        when(older.getId()).thenReturn(1L);
        when(newer.getId()).thenReturn(2L);
        when(delivery.getTrackingEvents()).thenReturn(List.of(older, newer));
        when(deliveries.findById(42L)).thenReturn(Optional.of(delivery));

        bridge.onCustomerStatusChanged(new CustomerStatusChange(42L, 2L));

        verify(publisher).publishDeliveryStatusChanged(delivery, newer);
    }

    @Test
    void brokerFailureDoesNotEscapeToCaller() {
        Delivery delivery = mock(Delivery.class);
        TrackingEvent event = mock(TrackingEvent.class);
        when(delivery.getTrackingEvents()).thenReturn(List.of(event));
        when(event.getId()).thenReturn(2L);
        when(deliveries.findById(42L)).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("Broker unavailable"))
                .when(publisher).publishDeliveryStatusChanged(delivery, event);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> bridge.onCustomerStatusChanged(new CustomerStatusChange(42L, 2L)));
    }
}
