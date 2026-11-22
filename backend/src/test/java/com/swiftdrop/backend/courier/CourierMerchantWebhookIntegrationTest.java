package com.swiftdrop.backend.courier;

import com.swiftdrop.backend.courier.dto.CourierStatusUpdateRequest;
import com.swiftdrop.backend.courier.dto.DeliveryConfirmationRequest;
import com.swiftdrop.backend.courier.dto.DeliveryFailureRequest;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import com.swiftdrop.backend.courier.exception.InvalidStatusTransitionException;
import com.swiftdrop.backend.courier.exception.UnauthorizedDeliveryAccessException;
import com.swiftdrop.backend.courier.service.CourierService;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.identity.entity.TestPeople;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.MerchantLifecycleWebhook;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.swiftdrop.backend.customer.entity.DeliveryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CourierMerchantWebhookIntegrationTest {
    @Autowired CourierService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RabbitTemplate rabbit;

    private TransactionTemplate tx;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final UUID wrongCourierId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private Long merchantId;
    private Long customerProfileId;
    private Long deliveryId;
    private Long customerDeliveryId;
    private String tracking;
    private String customerTracking;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            var courier = TestPeople.saved(em, courierId, "Courier Fixture", "COURIER");
            TestPeople.saved(em, wrongCourierId, "Wrong Courier", "COURIER");
            var merchant = new Merchant(TestPeople.saved(em, ownerId, "Merchant Fixture", "MERCHANT"));
            var customer = new Customer(TestPeople.saved(em, customerId, "Customer Fixture", "CUSTOMER"), "0771234567");
            em.persist(merchant);
            em.persist(customer);
            merchantId = merchant.getId();
            customerProfileId = customer.getId();
            tracking = "SD-" + UUID.randomUUID().toString().substring(0, 8);
            customerTracking = "SD-" + UUID.randomUUID().toString().substring(0, 8);
            var delivery = new Delivery(merchantId, tracking, UUID.randomUUID().toString(), DeliveryType.STANDARD);
            var customerDelivery = new Delivery(customer, customerTracking, UUID.randomUUID().toString(), DeliveryType.STANDARD);
            for (var shipment : List.of(delivery, customerDelivery)) {
                shipment.setStatus(CONFIRMED);
                shipment.assignCourier(courier);
                em.persist(shipment);
            }
            deliveryId = delivery.getId();
            customerDeliveryId = customerDelivery.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM delivery_proof WHERE delivery_id = ?", deliveryId);
            jdbc.update("DELETE FROM delivery_failure WHERE delivery_id = ?", deliveryId);
            jdbc.update("DELETE FROM delivery WHERE delivery_id IN (?, ?)", deliveryId, customerDeliveryId);
            jdbc.update("DELETE FROM merchant WHERE merchant_id = ?", merchantId);
            jdbc.update("DELETE FROM customer WHERE customer_id = ?", customerProfileId);
            jdbc.update("DELETE FROM app_users WHERE id IN (?, ?, ?, ?)", ownerId, courierId, wrongCourierId, customerId);
        });
    }

    @Test
    void publishesAllFiveStatusesAndKeepsDistinctIdsAcrossLegitimateRetry() {
        acknowledgeWebhook();
        move(tracking, PICKED_UP);
        move(tracking, IN_TRANSIT);
        move(tracking, OUT_FOR_DELIVERY);
        service.reportDeliveryFailure(courierId, tracking, new DeliveryFailureRequest(
                DeliveryFailureReason.RECIPIENT_UNAVAILABLE, "Private failure note", "Private location"));
        move(tracking, OUT_FOR_DELIVERY);
        service.confirmDelivery(courierId, tracking,
                new DeliveryConfirmationRequest("Private signature", null, null, null, null));

        var envelope = ArgumentCaptor.forClass(MerchantLifecycleWebhook.class);
        verify(rabbit, timeout(5000).times(6)).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantLifecycleWebhook.ROUTING_KEY), envelope.capture(), any(CorrelationData.class));
        var events = envelope.getAllValues();
        assertThat(events).extracting(e -> e.payload().status()).containsExactlyInAnyOrder(
                "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "FAILED", "OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(events).allSatisfy(e -> {
            assertThat(e.merchantId()).isEqualTo(merchantId);
            assertThat(e.payload().deliveryId()).isEqualTo(deliveryId);
            assertThat(e.payload().trackingNumber()).isEqualTo(tracking);
        });
        assertThat(events.stream().map(e -> e.payload().eventId())).containsExactlyInAnyOrderElementsOf(
                jdbc.queryForList("SELECT tracking_event_id FROM tracking_event WHERE delivery_id = ?", Long.class, deliveryId)
                        .stream().map(id -> "delivery-status-" + id).toList());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_proof WHERE delivery_id = ?", Integer.class, deliveryId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_failure WHERE delivery_id = ?", Integer.class, deliveryId)).isOne();
    }

    @Test
    void rollbackCustomerShipmentAndWrongCourierDoNotPublish() {
        tx.executeWithoutResult(status -> {
            move(tracking, PICKED_UP);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT status FROM delivery WHERE delivery_id = ?", String.class, deliveryId))
                .isEqualTo("CONFIRMED");
        move(customerTracking, PICKED_UP);
        assertThatThrownBy(() -> service.updateDeliveryStatus(wrongCourierId, tracking,
                new CourierStatusUpdateRequest(PICKED_UP, null, null)))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class);
        verify(rabbit, after(300).never()).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantLifecycleWebhook.ROUTING_KEY), any(MerchantLifecycleWebhook.class), any(CorrelationData.class));
    }

    @Test
    void concurrentAndRepeatedRequestsCommitOnlyOneTransition() throws Exception {
        acknowledgeWebhook();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> pickup(start));
            var second = workers.submit(() -> pickup(start));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThatThrownBy(() -> move(tracking, PICKED_UP)).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tracking_event WHERE delivery_id = ?", Integer.class, deliveryId)).isOne();
        verify(rabbit, timeout(5000)).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantLifecycleWebhook.ROUTING_KEY), any(MerchantLifecycleWebhook.class), any(CorrelationData.class));
    }

    private boolean pickup(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            move(tracking, PICKED_UP);
            return true;
        } catch (InvalidStatusTransitionException repeated) {
            return false;
        }
    }

    private void move(String number, DeliveryStatus next) {
        service.updateDeliveryStatus(courierId, number, new CourierStatusUpdateRequest(next, null, null));
    }

    private void acknowledgeWebhook() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantLifecycleWebhook.ROUTING_KEY), any(MerchantLifecycleWebhook.class), any(CorrelationData.class));
    }
}
