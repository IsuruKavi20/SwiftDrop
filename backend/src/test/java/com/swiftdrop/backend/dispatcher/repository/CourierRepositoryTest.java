package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.identity.TestPeople;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CourierRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierRepository repository;

    @Test
    void looksUpProfileByAuthUserIdAndHandlesUnknownUsers() {
        Courier first = courier("First", "COURIER", CourierAvailability.OFFLINE);
        Courier second = courier("Second", "COURIER", CourierAvailability.AVAILABLE);
        assertThat(repository.findByUserId(first.getUser().getId())).get()
                .extracting(Courier::getId).isEqualTo(first.getId());
        assertThat(repository.findByUserId(second.getUser().getId())).get()
                .extracting(Courier::getId).isEqualTo(second.getId());
        assertThat(repository.existsByUserId(first.getUser().getId())).isTrue();
        UUID unknown = UUID.randomUUID();
        assertThat(repository.findByUserId(unknown)).isEmpty();
        assertThat(repository.existsByUserId(unknown)).isFalse();
    }

    @Test
    void availabilityFilterPaginatesProfilesInStableOrder() {
        Courier first = courier("First", "COURIER", CourierAvailability.AVAILABLE);
        Courier second = courier("Second", "COURIER", CourierAvailability.AVAILABLE);
        Courier busy = courier("Busy", "COURIER", CourierAvailability.BUSY);
        courier("Offline", "COURIER", CourierAvailability.OFFLINE);

        var page = repository.findByAvailabilityOrderByIdAsc(CourierAvailability.AVAILABLE, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page).extracting(Courier::getId).containsExactly(first.getId());
        assertThat(repository.findByAvailabilityOrderByIdAsc(CourierAvailability.AVAILABLE, PageRequest.of(1, 1)))
                .extracting(Courier::getId).containsExactly(second.getId());
        assertThat(repository.findByAvailabilityOrderByIdAsc(CourierAvailability.BUSY, Pageable.unpaged()))
                .extracting(Courier::getId).containsExactly(busy.getId());
    }

    @Test
    void candidatesExcludeUnavailableDisabledLockedAndNonCourierAccounts() {
        Courier first = courier("First", "COURIER", CourierAvailability.AVAILABLE);
        Courier second = courier("Second", "COURIER", CourierAvailability.AVAILABLE);
        courier("Busy", "COURIER", CourierAvailability.BUSY);
        courier("Offline", "COURIER", CourierAvailability.OFFLINE);
        Courier disabled = courier("Disabled", "COURIER", CourierAvailability.AVAILABLE);
        Courier locked = courier("Locked", "COURIER", CourierAvailability.AVAILABLE);
        courier("Wrong role", "CUSTOMER", CourierAvailability.AVAILABLE);
        em.flush();
        // Simulate refreshed auth snapshots without changing the read-only AppUser entity API.
        em.createNativeQuery("update app_users set active = false, status = 'DISABLED' where id = :id")
                .setParameter("id", disabled.getUser().getId()).executeUpdate();
        em.createNativeQuery("update app_users set status = 'LOCKED' where id = :id")
                .setParameter("id", locked.getUser().getId()).executeUpdate();
        em.clear();

        var page = repository.findAvailableForAssignment(PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page).extracting(Courier::getId).containsExactly(first.getId());
        assertThat(repository.findAvailableForAssignment(PageRequest.of(1, 1)))
                .extracting(Courier::getId).containsExactly(second.getId());
        assertThat(repository.findByAvailabilityOrderByIdAsc(CourierAvailability.AVAILABLE, Pageable.unpaged()))
                .extracting(Courier::getId).contains(disabled.getId(), locked.getId());
    }

    @Test
    void assignmentLookupAcquiresAWriteLockAndHandlesMissingProfiles() {
        Courier courier = courier("Courier", "COURIER", CourierAvailability.AVAILABLE);
        em.flush();
        em.clear();
        Courier locked = repository.findByIdForUpdate(courier.getId()).orElseThrow();
        assertThat(em.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(repository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
    }

    private Courier courier(String name, String role, CourierAvailability availability) {
        Courier courier = new Courier(TestPeople.saved(em, name, role), "0771234567");
        courier.setAvailability(availability);
        return repository.saveAndFlush(courier);
    }
}
