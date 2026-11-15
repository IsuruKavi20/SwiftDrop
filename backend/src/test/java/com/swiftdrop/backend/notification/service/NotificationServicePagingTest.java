package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.customer.repository.CustomerRepository;
import com.swiftdrop.backend.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServicePagingTest {

    private NotificationLogRepository logs;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        logs = mock(NotificationLogRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findByUserId(any())).thenReturn(Optional.empty());
        when(logs.findForCustomer(any(), any(), any(), any())).thenReturn(Page.empty());
        service = new NotificationService(logs, customers, mock(CustomerDeliveryRepository.class),
                mock(NotificationPublisherService.class));
    }

    private Pageable requested(int page, int size) {
        service.getMyNotifications(UUID.randomUUID(), "someone@test.com", page, size);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(logs).findForCustomer(isNull(), isNull(), eq("someone@test.com"), pageable.capture());
        return pageable.getValue();
    }

    @Test
    @DisplayName("a huge page size is capped, so one request can't load the whole history")
    void hugeSizeIsCapped() {
        assertThat(requested(0, 1_000_000).getPageSize()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("a normal page size is kept")
    void normalSizeIsKept() {
        assertThat(requested(2, 10).getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("zero or negative values become the first page with one item")
    void badValuesAreRaised() {
        Pageable pageable = requested(-3, 0);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(1);
    }
}
