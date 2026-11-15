package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Operational courier profiles for dispatcher management (234140P). */
public interface CourierRepository extends JpaRepository<Courier, Long> {

    /** Auth user UUID, not the courier profile's numeric primary key. */
    Optional<Courier> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /** Profiles of these people (e.g. the couriers on a page of deliveries), in one query. */
    List<Courier> findByUserIdIn(Collection<UUID> userIds);

    /** Management view includes matching profiles even when the linked auth account is disabled. */
    Page<Courier> findByAvailabilityOrderByIdAsc(CourierAvailability availability, Pageable pageable);

    /**
     * Assignment candidates according to the local auth snapshot. Before assigning, the service must
     * use UserDirectory, lock the courier, and recheck availability, account eligibility and workload.
     */
    @Query("""
            select c from Courier c join c.user u
            where c.availability = com.swiftdrop.backend.dispatcher.entity.CourierAvailability.AVAILABLE
              and u.active = true
              and u.status = com.swiftdrop.backend.identity.entity.AppUserStatus.ACTIVE
              and 'COURIER' member of u.roles
            order by c.id asc
            """)
    Page<Courier> findAvailableForAssignment(Pageable pageable);

    /** Call in a write transaction; keep the lock through workload checks and assignment persistence. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Courier c where c.id = :id")
    Optional<Courier> findByIdForUpdate(@Param("id") Long id);
}
