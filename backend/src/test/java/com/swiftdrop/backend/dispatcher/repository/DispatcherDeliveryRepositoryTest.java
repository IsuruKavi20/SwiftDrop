package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DispatcherDeliveryRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private DispatcherDeliveryRepository repository;

    private Delivery first;
    private Delivery second;
    private Delivery third;

    @BeforeEach
    void setUp() {
        Customer alice = customer("Alice");
        Customer bob = customer("Bob");
        first = delivery(alice, "SD-100001", "Kamal", "Kandy", DeliveryStatus.PENDING);
        first.setAddress(new DeliveryAddress(AddressType.PICKUP, "Sender", "0771111111",
                "12 Warehouse Road", "Colombo", "Colombo", null));
        second = delivery(bob, "SD-100002", "Nisha", "Galle", DeliveryStatus.IN_TRANSIT);
        second.assignCourier(TestPeople.saved(em, "Nimal Courier", "COURIER"));
        third = delivery(bob, "SD-100003", "Ruwan", "Kandy", DeliveryStatus.DELIVERED);
        third.setAddress(new DeliveryAddress(AddressType.PICKUP, "Sender", "0771111111",
                "1 Lake Road", "Kandy", "Kandy", null));
        em.flush();
        // Equal timestamps exercise the ID tie-breaker for stable pagination.
        em.createNativeQuery("update delivery set created_at = TIMESTAMP '2026-11-07 08:00:00'").executeUpdate();
        em.clear();
    }

    @Test
    void dispatcherCanListAndLookUpOrdersAcrossCustomersIncludingUnassignedOrders() {
        assertThat(repository.search(null, null, Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(repository.search(null, "  ", Pageable.unpaged())).hasSize(3);
        assertThat(repository.findByTrackingNumber("SD-100002")).get()
                .extracting(Delivery::getId).isEqualTo(second.getId());
        assertThat(repository.findByTrackingNumber("missing")).isEmpty();
        assertThat(repository.findById(first.getId())).isPresent();
    }

    @Test
    void searchesTrackingNumbersContactsBothLocationsAndCourierNamesWithStatusFiltering() {
        assertThat(repository.search(null, "100002", Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(second.getId());
        assertThat(repository.search(null, " kamAL ", Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(first.getId());
        assertThat(repository.search(null, "WAREHOUSE", Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(first.getId());
        assertThat(repository.search(null, "colombo", Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(first.getId());
        assertThat(repository.search(null, "nimal", Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(second.getId());
        assertThat(repository.search(DeliveryStatus.DELIVERED, "kandy", Pageable.unpaged()))
                .extracting(Delivery::getId).containsExactly(third.getId());
        assertThat(repository.search(DeliveryStatus.FAILED, null, Pageable.unpaged())).isEmpty();
        assertThat(repository.search(DeliveryStatus.PENDING, "galle", Pageable.unpaged())).isEmpty();
    }

    @Test
    void paginationCountsDeliveriesOnceEvenWhenBothAddressesMatch() {
        var page = repository.search(null, "kandy", PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page).extracting(Delivery::getId).containsExactly(third.getId());
        assertThat(repository.search(null, "kandy", PageRequest.of(1, 1)))
                .extracting(Delivery::getId).containsExactly(first.getId());
        assertThat(repository.search(null, "kandy", PageRequest.of(2, 1))).isEmpty();
    }

    @Test
    void searchTreatsWildcardAndEscapeCharactersLiterally() {
        Delivery special = delivery(customer("Special"), "SD-50%_\\", "Special", "Matara", DeliveryStatus.PENDING);
        em.flush();
        for (String text : new String[]{"%", "_", "\\", "50%_\\"}) {
            assertThat(repository.search(null, text, Pageable.unpaged()))
                    .extracting(Delivery::getId).containsExactly(special.getId());
        }
    }

    @Test
    void dashboardCountsIncludeAllCustomersAndStatusesWithoutAddressMultiplication() {
        Map<DeliveryStatus, Long> counts = repository.countByStatus().stream().collect(Collectors.toMap(
                DispatcherDeliveryRepository.StatusCount::getStatus, DispatcherDeliveryRepository.StatusCount::getTotal));
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(
                DeliveryStatus.PENDING, 1L, DeliveryStatus.IN_TRANSIT, 1L, DeliveryStatus.DELIVERED, 1L));
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void assignmentLookupAcquiresAWriteLockAndHandlesMissingDeliveries() {
        Delivery locked = repository.findByIdForUpdate(first.getId()).orElseThrow();
        assertThat(em.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(repository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
    }

    private Customer customer(String name) {
        Customer customer = new Customer(TestPeople.saved(em, name, "CUSTOMER"), "0771234567");
        em.persist(customer);
        return customer;
    }

    private Delivery delivery(Customer customer, String tracking, String recipient, String city, DeliveryStatus status) {
        Delivery delivery = new Delivery(customer, tracking, UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.setStatus(status);
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, recipient, "0711234567",
                "1 Main Street", city, city, null));
        em.persist(delivery);
        return delivery;
    }
}
