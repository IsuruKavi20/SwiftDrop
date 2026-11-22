package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.notification.entity.MerchantWebhookConfig;
import com.swiftdrop.backend.notification.entity.WebhookDeliveryLog;
import com.swiftdrop.backend.notification.entity.WebhookStatus;
import com.swiftdrop.backend.notification.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.backend.notification.repository.WebhookDeliveryLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookDispatcherServiceTest {

    @ParameterizedTest
    @CsvSource({"CONFIRMED, DELIVERY_CONFIRMED", "CONFIRMED, CONFIRMED", "CONFIRMED, *", "COURIER_ASSIGNED, COURIER_ASSIGNED",
            "PICKED_UP, DELIVERY_PICKED_UP", "IN_TRANSIT, DELIVERY_IN_TRANSIT",
            "OUT_FOR_DELIVERY, DELIVERY_OUT_FOR_DELIVERY", "DELIVERED, DELIVERY_DELIVERED",
            "FAILED, 'DELIVERY_FAILED,DELIVERY_STATUS_CHANGED,FAILED'", "FAILED, *",
            "PICKED_UP, DELIVERY_STATUS_CHANGED"})
    void retriesOnlySubscribedMerchantEndpointWithTheSameSignedEventId(String eventType, String subscription) throws Exception {
        var configs = mock(MerchantWebhookConfigRepository.class);
        var logs = mock(WebhookDeliveryLogRepository.class);
        var client = mock(HttpClient.class);
        var entities = mock(EntityManager.class);
        var delivery = mock(Delivery.class);
        when(delivery.getMerchantId()).thenReturn(42L);
        when(entities.find(Delivery.class, 5L)).thenReturn(delivery);
        var dispatcher = new WebhookDispatcherService(configs, logs, entities);
        dispatcher.dispatchWebhook(43L, 5L, "CONFIRMED", "{}", "delivery-status-7");
        verifyNoInteractions(configs, logs, client);
        ReflectionTestUtils.setField(dispatcher, "httpClient", client);
        var subscribed = new MerchantWebhookConfig(null, "https://192.0.2.1/confirmed", "test-signing-key", subscription);
        var unrelated = new MerchantWebhookConfig(null, "https://192.0.2.2/other", "test-signing-key", "CANCELLED");
        when(configs.findByMerchant_IdAndActiveTrue(42L)).thenReturn(List.of(subscribed, unrelated));
        var failure = mock(HttpResponse.class);
        var recovery = mock(HttpResponse.class);
        when(failure.statusCode()).thenReturn(503);
        when(recovery.statusCode()).thenReturn(204);
        doReturn(failure, recovery).when(client).send(any(HttpRequest.class), any());

        String payload = "{\"eventId\":\"delivery-status-7\",\"status\":\"CONFIRMED\"}";
        dispatcher.dispatchWebhook(42L, 5L, eventType, payload, "delivery-status-7");

        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(configs).findByMerchant_IdAndActiveTrue(42L);
        verifyNoMoreInteractions(configs);
        verify(client, times(2)).send(requests.capture(), any());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.uri().getHost()).isEqualTo("192.0.2.1");
            assertThat(request.headers().firstValue("X-SwiftDrop-Event-Id")).contains("delivery-status-7");
            assertThat(request.headers().firstValue("X-SwiftDrop-Signature"))
                    .contains(WebhookDispatcherService.computeHmacSha256(payload, "test-signing-key"));
        });
        var entry = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(logs, atLeastOnce()).save(entry.capture());
        assertThat(entry.getValue().getStatus()).isEqualTo(WebhookStatus.SUCCESS);
        assertThat(entry.getValue().getAttemptCount()).isEqualTo(2);
        assertThat(entry.getValue().getDelivery()).isSameAs(delivery);
    }
}
