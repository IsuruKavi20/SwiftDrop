package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.identity.entity.AppUser;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CourierAssignmentRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierAssignmentRepository repository;

    private Courier alice;
    private Courier bob;
    private Courier idle;
    private AppUser dispatcher;
    private Customer customer;
    private Delivery reassignedDelivery;
    private Delivery closedDelivery;
    private CourierAssignment oldAssignment;
    private CourierAssignment currentAssignment;
    private CourierAssignment alicesAssignment;
    private CourierAssignment bobsOtherAssignment;
    private final Instant assignedAt = Instant.parse("2026-11-07T08:00:00Z");

    @BeforeEach
    void setUp() {
        alice = courier("Alice");
        bob = courier("Bob");
        idle = courier("Idle");
        dispatcher = TestPeople.saved(em, "Dispatcher", "DISPATCHER");
        customer = new Customer(TestPeople.saved(em, "Customer", "CUSTOMER"), "0771234567");
        em.persist(customer);

        reassignedDelivery = delivery();
        oldAssignment = assignment(reassignedDelivery, alice);
        oldAssignment.endAssignment(dispatcher, assignedAt, "Reassigned");
        repository.flush();
        // Same timestamp also checks deterministic history ordering by assignment ID.
        currentAssignment = assignment(reassignedDelivery, bob);
        alicesAssignment = assignment(delivery(), alice);
        bobsOtherAssignment = assignment(delivery(), bob);
        closedDelivery = delivery();
        CourierAssignment closed = assignment(closedDelivery, alice);
        closed.endAssignment(dispatcher, assignedAt.plusSeconds(60), "Completed");
        repository.flush();
        em.clear();
    }

    @Test
    void currentLookupIgnoresHistoricalAndEndedAssignments() {
        assertThat(repository.findByDeliveryIdAndActiveTrue(reassignedDelivery.getId())).get()
                .extracting(CourierAssignment::getId).isEqualTo(currentAssignment.getId());
        assertThat(repository.findByDeliveryIdAndActiveTrue(closedDelivery.getId())).isEmpty();
        assertThat(repository.findByDeliveryIdAndActiveTrue(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void courierTaskListsAreScopedToTheRequestedProfileOrAuthUser() {
        assertThat(repository.findByCourierIdAndActiveTrueOrderByAssignedAtAscIdAsc(alice.getId(), Pageable.unpaged()))
                .extracting(CourierAssignment::getId).containsExactly(alicesAssignment.getId());
        var bobsPage = repository.findByCourierIdAndActiveTrueOrderByAssignedAtAscIdAsc(bob.getId(), PageRequest.of(0, 1));
        assertThat(bobsPage.getTotalElements()).isEqualTo(2);
        assertThat(bobsPage).extracting(CourierAssignment::getId).containsExactly(currentAssignment.getId());
        assertThat(repository.findByCourierIdAndActiveTrueOrderByAssignedAtAscIdAsc(bob.getId(), PageRequest.of(1, 1)))
                .extracting(CourierAssignment::getId).containsExactly(bobsOtherAssignment.getId());
        assertThat(repository.findByCourierUserIdAndActiveTrueOrderByAssignedAtAscIdAsc(bob.getUser().getId(), Pageable.unpaged()))
                .extracting(CourierAssignment::getId).containsExactly(currentAssignment.getId(), bobsOtherAssignment.getId());
        assertThat(repository.findByCourierUserIdAndActiveTrueOrderByAssignedAtAscIdAsc(UUID.randomUUID(), Pageable.unpaged()))
                .isEmpty();
    }

    @Test
    void historyIncludesClosedRowsAndPaginatesNewestFirstWhenTimestampsTie() {
        var page = repository.findByDeliveryIdOrderByAssignedAtDescIdDesc(reassignedDelivery.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page).extracting(CourierAssignment::getId).containsExactly(currentAssignment.getId());
        var historyPage = repository.findByDeliveryIdOrderByAssignedAtDescIdDesc(reassignedDelivery.getId(), PageRequest.of(1, 1));
        assertThat(historyPage).extracting(CourierAssignment::getId).containsExactly(oldAssignment.getId());
        assertThat(historyPage.getContent().getFirst().isActive()).isFalse();
        assertThat(historyPage.getContent().getFirst().getEndReason()).isEqualTo("Reassigned");
        assertThat(repository.findByDeliveryIdOrderByAssignedAtDescIdDesc(Long.MAX_VALUE, Pageable.unpaged())).isEmpty();
    }

    @Test
    void workloadsCountOnlyCurrentAssignmentsAndReturnZeroForIdleCouriers() {
        assertThat(repository.countByCourierIdAndActiveTrue(alice.getId())).isEqualTo(1);
        assertThat(repository.countByCourierIdAndActiveTrue(bob.getId())).isEqualTo(2);
        assertThat(repository.countByCourierIdAndActiveTrue(idle.getId())).isZero();
        Map<Long, Long> workloads = repository.countActiveByCourier().stream().collect(Collectors.toMap(
                CourierAssignmentRepository.WorkloadCount::getCourierId,
                CourierAssignmentRepository.WorkloadCount::getTotal));
        assertThat(workloads).containsExactlyInAnyOrderEntriesOf(Map.of(alice.getId(), 1L, bob.getId(), 2L));
    }

    @Test
    void activeLookupLocksTheCurrentAssignmentWithoutSelectingHistory() {
        CourierAssignment locked = repository.findActiveByDeliveryIdForUpdate(reassignedDelivery.getId()).orElseThrow();
        assertThat(locked.getId()).isEqualTo(currentAssignment.getId());
        assertThat(em.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(repository.findActiveByDeliveryIdForUpdate(closedDelivery.getId())).isEmpty();
    }

    private Courier courier(String name) {
        Courier courier = new Courier(TestPeople.saved(em, name, "COURIER"), "0771234567");
        em.persist(courier);
        return courier;
    }

    private Delivery delivery() {
        Delivery delivery = new Delivery(customer, "SD-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString(), DeliveryType.STANDARD);
        em.persist(delivery);
        return delivery;
    }

    private CourierAssignment assignment(Delivery delivery, Courier courier) {
        return repository.saveAndFlush(new CourierAssignment(delivery, courier, dispatcher, assignedAt));
    }
}
