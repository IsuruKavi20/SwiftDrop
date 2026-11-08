package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assignment records for 234140P. Active rows store TRUE; historical rows store NULL, never FALSE.
 * Mutations and permission/ownership checks belong in a transactional service.
 */
public interface CourierAssignmentRepository extends JpaRepository<CourierAssignment, Long> {

    Optional<CourierAssignment> findByDeliveryIdAndActiveTrue(Long deliveryId);

    Page<CourierAssignment> findByCourierIdAndActiveTrueOrderByAssignedAtAscIdAsc(Long courierId, Pageable pageable);

    /** The service supplies the authenticated user's UUID, not a user-selected identity. */
    Page<CourierAssignment> findByCourierUserIdAndActiveTrueOrderByAssignedAtAscIdAsc(UUID userId, Pageable pageable);

    /** Full assignment history, including the current assignment; newest first with a stable tie-breaker. */
    Page<CourierAssignment> findByDeliveryIdOrderByAssignedAtDescIdDesc(Long deliveryId, Pageable pageable);

    long countByCourierIdAndActiveTrue(Long courierId);

    /** One query for fleet workloads. Couriers with no active assignments are absent; treat them as zero. */
    @Query("""
            select a.courier.id as courierId, count(a) as total
            from CourierAssignment a where a.active = true
            group by a.courier.id
            """)
    List<WorkloadCount> countActiveByCourier();

    interface WorkloadCount {
        Long getCourierId();
        long getTotal();
    }

    /**
     * Call inside a write transaction after locking the delivery. A missing assignment cannot be locked,
     * so this query alone does not serialize first assignments. Lock relevant couriers before workload
     * checks and flush the ended assignment before saving a replacement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CourierAssignment a where a.delivery.id = :deliveryId and a.active = true")
    Optional<CourierAssignment> findActiveByDeliveryIdForUpdate(@Param("deliveryId") Long deliveryId);
}
