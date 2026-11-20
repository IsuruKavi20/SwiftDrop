package com.swiftdrop.backend.courier.repository;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Delivery query repository for Courier operations and Dispatcher assignment workflows.
 * Operates on the shared {@link Delivery} entity without modifying customer-facing repositories.
 */
@Repository
public interface CourierDeliveryRepository extends JpaRepository<Delivery, Long> {

    /** All deliveries assigned to a specific courier, newest first. */
    @Query("SELECT d FROM Delivery d WHERE d.courier.id = :courierId ORDER BY d.createdAt DESC")
    Page<Delivery> findByCourierIdOrderByCreatedAtDesc(@Param("courierId") UUID courierId, Pageable pageable);

    /** Loads a delivery by tracking number regardless of assignment. */
    Optional<Delivery> findByTrackingNumber(String trackingNumber);

    /** Loads a specific delivery only if assigned to this courier (horizontal privilege check). */
    @Query("SELECT d FROM Delivery d WHERE d.trackingNumber = :trackingNumber AND d.courier.id = :courierId")
    Optional<Delivery> findByTrackingNumberAndCourierId(@Param("trackingNumber") String trackingNumber,
                                                        @Param("courierId") UUID courierId);

    /** Count deliveries for a courier filtered by lifecycle status (used for dashboard metrics). */
    @Query("SELECT count(d) FROM Delivery d WHERE d.courier.id = :courierId AND d.status = :status")
    long countByCourierIdAndStatus(@Param("courierId") UUID courierId, @Param("status") DeliveryStatus status);

    /** All deliveries assigned to a courier with a given status. */
    @Query("SELECT d FROM Delivery d WHERE d.courier.id = :courierId AND d.status = :status")
    List<Delivery> findByCourierIdAndStatus(@Param("courierId") UUID courierId, @Param("status") DeliveryStatus status);

    /**
     * Search courier's assigned deliveries with optional status filter and text pattern
     * matched against tracking number, recipient contact name, or destination city.
     */
    @Query(value = """
            select d from Delivery d
            where d.courier.id = :courierId
              and (:status is null or d.status = :status)
              and (lower(d.trackingNumber) like :pattern escape '\\'
                   or exists (select 1 from DeliveryAddress a
                              where a.delivery = d and a.addressType = com.swiftdrop.backend.customer.entity.AddressType.DESTINATION
                                and (lower(a.contactName) like :pattern escape '\\'
                                     or lower(a.city) like :pattern escape '\\')))
            order by d.createdAt desc
            """)
    Page<Delivery> searchByCourier(@Param("courierId") UUID courierId,
                                   @Param("status") DeliveryStatus status,
                                   @Param("pattern") String pattern,
                                   Pageable pageable);

    /**
     * Unassigned deliveries available for Dispatcher assignment.
     */
    @Query("SELECT d FROM Delivery d WHERE d.courier IS NULL AND d.status = :status")
    List<Delivery> findByCourierIsNullAndStatus(@Param("status") DeliveryStatus status);
}
