package com.swiftdrop.backend.dispatcher.entity;

import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DispatcherEntitiesTest {

    @Autowired
    private EntityManager em;

    @Test
    void persistsCourierDetailsAndAssignmentAlongsideTheExistingDelivery() {
        Courier courier = courier("Nimal");
        courier.setVehicleType("MOTORCYCLE");
        courier.setVehicleNumber("BCA-1234");
        courier.setAvailability(CourierAvailability.AVAILABLE);
        Delivery delivery = delivery();
        AppUser dispatcher = TestPeople.saved(em, "Dispatcher", "DISPATCHER");
        Instant assignedAt = Instant.parse("2026-11-07T08:00:00Z");
        CourierAssignment assignment = new CourierAssignment(delivery, courier, dispatcher, assignedAt);
        delivery.assignCourier(courier.getUser());
        em.persist(assignment);
        em.flush();
        em.clear();

        CourierAssignment saved = em.find(CourierAssignment.class, assignment.getId());
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getAssignedAt()).isEqualTo(assignedAt);
        assertThat(saved.getAssignedBy().getId()).isEqualTo(dispatcher.getId());
        assertThat(saved.getDelivery().getCourier().getId()).isEqualTo(courier.getUser().getId());
        assertThat(saved.getCourier().getUser().getName()).contains("Nimal");
        assertThat(saved.getCourier().getAvailability()).isEqualTo(CourierAvailability.AVAILABLE);
        assertThat(saved.getCourier().getVehicleNumber()).isEqualTo("BCA-1234");
        assertThat(saved.getCourier().getCreatedAt()).isNotNull();
        assertThat(saved.getCourier().getUpdatedAt()).isNotNull();
    }

    @Test
    void multipleReassignmentsPreserveHistoryAndAllowOnlyOneCurrentAssignment() {
        Courier first = courier("First");
        Courier second = courier("Second");
        Delivery delivery = delivery();
        AppUser dispatcher = TestPeople.saved(em, "Dispatcher", "DISPATCHER");
        Instant start = Instant.parse("2026-11-07T08:00:00Z");
        CourierAssignment original = new CourierAssignment(delivery, first, dispatcher, start);
        em.persist(original);
        original.endAssignment(dispatcher, start.plusSeconds(60), "Courier unavailable");
        em.flush();
        CourierAssignment replacement = new CourierAssignment(delivery, second, dispatcher, start.plusSeconds(60));
        em.persist(replacement);
        replacement.endAssignment(dispatcher, start.plusSeconds(120), "Route changed");
        em.flush();
        CourierAssignment current = new CourierAssignment(delivery, first, dispatcher, start.plusSeconds(120));
        em.persist(current);
        delivery.assignCourier(first.getUser());
        em.flush();
        em.clear();

        var history = em.createQuery("select a from CourierAssignment a where a.delivery.id = :id order by a.assignedAt",
                        CourierAssignment.class).setParameter("id", delivery.getId()).getResultList();
        assertThat(history).hasSize(3);
        assertThat(history).extracting(CourierAssignment::isActive).containsExactly(false, false, true);
        assertThat(history.getFirst().getEndReason()).isEqualTo("Courier unavailable");
        assertThat(history.getFirst().getEndedBy().getId()).isEqualTo(dispatcher.getId());
        assertThat(history.getFirst().getEndedAt()).isEqualTo(start.plusSeconds(60));
        assertThat(history.getLast().getCourier().getId()).isEqualTo(first.getId());
    }

    @Test
    void databaseRejectsTwoActiveAssignmentsForOneDelivery() {
        Courier first = courier("First");
        Courier second = courier("Second");
        Delivery delivery = delivery();
        AppUser dispatcher = TestPeople.saved(em, "Dispatcher", "DISPATCHER");
        em.persist(new CourierAssignment(delivery, first, dispatcher, Instant.now()));
        em.flush();

        assertThatThrownBy(() -> {
            em.persist(new CourierAssignment(delivery, second, dispatcher, Instant.now()));
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void oneCourierCanCarryMultipleDeliveries() {
        Courier courier = courier("Courier");
        AppUser dispatcher = TestPeople.saved(em, "Dispatcher", "DISPATCHER");
        em.persist(new CourierAssignment(delivery(), courier, dispatcher, Instant.now()));
        em.persist(new CourierAssignment(delivery(), courier, dispatcher, Instant.now()));
        em.flush();

        assertThat(em.createQuery("select count(a) from CourierAssignment a where a.courier.id = :id and a.active = true",
                Long.class).setParameter("id", courier.getId()).getSingleResult()).isEqualTo(2L);
    }

    @Test
    void databaseRejectsDuplicateCourierProfilesForTheSameUser() {
        Courier original = courier("Courier");
        assertThatThrownBy(() -> {
            em.persist(new Courier(original.getUser(), "0772222222"));
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void staleAvailabilityUpdatesCannotOverwriteNewerChanges() {
        Courier original = courier("Courier");
        assertThat(original.getAvailability()).isEqualTo(CourierAvailability.OFFLINE);
        em.flush();
        em.detach(original);
        Courier current = em.find(Courier.class, original.getId());
        current.setAvailability(CourierAvailability.BUSY);
        em.flush();
        original.setAvailability(CourierAvailability.AVAILABLE);

        assertThatThrownBy(() -> {
            em.merge(original);
            em.flush();
        }).isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void assignmentCannotEndBeforeItStartsOrBeClosedTwice() {
        AppUser dispatcher = TestPeople.unsaved("Dispatcher", "DISPATCHER");
        Instant start = Instant.parse("2026-11-07T08:00:00Z");
        CourierAssignment assignment = new CourierAssignment(delivery(), courier("Courier"), dispatcher, start);
        assertThatThrownBy(() -> assignment.endAssignment(dispatcher, start.minusSeconds(1), "Invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(assignment.isActive()).isTrue();
        assignment.endAssignment(dispatcher, start.plusSeconds(1), "Reassigned");
        assertThatThrownBy(() -> assignment.endAssignment(dispatcher, start.plusSeconds(2), "Again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void removingAnAssignmentDoesNotCascadeToExistingDeliveryOrUserRecords() {
        Courier courier = courier("Courier");
        Delivery delivery = delivery();
        AppUser dispatcher = TestPeople.saved(em, "Dispatcher", "DISPATCHER");
        CourierAssignment assignment = new CourierAssignment(delivery, courier, dispatcher, Instant.now());
        em.persist(assignment);
        em.flush();
        em.remove(assignment);
        em.flush();
        em.clear();

        assertThat(em.find(Delivery.class, delivery.getId())).isNotNull();
        assertThat(em.find(Courier.class, courier.getId())).isNotNull();
        assertThat(em.find(AppUser.class, dispatcher.getId())).isNotNull();
        assertThat(em.find(AppUser.class, courier.getUser().getId())).isNotNull();
    }

    private Courier courier(String name) {
        Courier courier = new Courier(TestPeople.saved(em, name, "COURIER"), "0771234567");
        em.persist(courier);
        return courier;
    }

    private Delivery delivery() {
        Customer customer = new Customer(TestPeople.saved(em, "Customer", "CUSTOMER"), "0711234567");
        em.persist(customer);
        Delivery delivery = new Delivery(customer, "SD-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString(), DeliveryType.STANDARD);
        em.persist(delivery);
        return delivery;
    }
}
