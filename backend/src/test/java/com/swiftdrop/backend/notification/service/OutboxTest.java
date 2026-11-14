package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.service.CustomerStatusChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox end to end on the test database: an event published in a transaction is stored with it; a listener
 * that fails leaves it open; resubmitting delivers it; a delivered event is removed.
 */
@SpringBootTest(properties = "swiftdrop.outbox.resubmit-every=PT1H")
class OutboxTest {

    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;
    @Autowired IncompleteEventPublications incomplete;
    @Autowired FlakyListener flaky;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM event_publication");
        flaky.reset(0);
    }

    @Test
    void aDeliveredEventIsRemovedFromTheOutbox() {
        transaction.executeWithoutResult(status -> events.publishEvent(new CustomerStatusChange(-1L, -1L)));

        waitUntil(() -> flaky.delivered() == 1);
        waitUntil(() -> openEvents() == 0);
    }

    @Test
    void aFailedEventStaysOpenAndIsDeliveredWhenResubmitted() {
        flaky.reset(1);   // e.g. RabbitMQ is down for the first try

        transaction.executeWithoutResult(status -> events.publishEvent(new CustomerStatusChange(-2L, -2L)));
        waitUntil(() -> flaky.failuresLeft() == 0);
        waitUntil(() -> openEvents() >= 1);
        assertThat(flaky.delivered()).isZero();

        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ZERO);

        waitUntil(() -> flaky.delivered() == 1);
    }

    @Test
    void nothingIsStoredWhenTheTransactionRollsBack() {
        transaction.executeWithoutResult(status -> {
            events.publishEvent(new CustomerStatusChange(-3L, -3L));
            status.setRollbackOnly();   // e.g. the booking failed: no "status changed" may be sent
        });

        assertThat(openEvents()).isZero();
        assertThat(flaky.delivered()).isZero();
    }

    private int openEvents() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
        return count == null ? 0 : count;
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition not met in 10 s").isLessThan(deadline);
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @TestConfiguration
    static class Listeners {
        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }

    /** A test listener: fails while failuresLeft > 0, otherwise counts the delivery. */
    // Methods, not fields: the bean is a Spring proxy (async + transactional), and a proxy's fields are empty
    static class FlakyListener {
        private final AtomicInteger failuresLeft = new AtomicInteger();
        private final AtomicInteger delivered = new AtomicInteger();

        public void reset(int failures) {
            failuresLeft.set(failures);
            delivered.set(0);
        }

        public int failuresLeft() {
            return failuresLeft.get();
        }

        public int delivered() {
            return delivered.get();
        }

        @ApplicationModuleListener
        void on(CustomerStatusChange event) {
            if (event.deliveryId() >= 0) {
                return; // only this test's events
            }
            if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("broker down (test)");
            }
            delivered.incrementAndGet();
        }
    }
}
