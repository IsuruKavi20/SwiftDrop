package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Deliveries of the Customer Module. Named CustomerDeliveryRepository because the placeholder
 * {@code delivery.DeliveryRepository} bean still exists; rename once that placeholder is removed.
 *
 * <p>Customer-facing lookups always take the customer id, so a customer can only ever load their own deliveries.
 * Child collections (addresses, parcels, tracking events) load lazily: read them inside a @Transactional service.
 */
public interface CustomerDeliveryRepository extends JpaRepository<Delivery, Long> {

    // ---- Booking ----

    boolean existsByTrackingNumber(String trackingNumber);

    // ---- My Deliveries / delivery details ----

    /** All of a customer's deliveries, newest first. */
    Page<Delivery> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /** One delivery, only if it belongs to this customer. */
    Optional<Delivery> findByIdAndCustomerId(Long id, Long customerId);

    /** Track Parcel for a logged-in customer: only their own delivery. */
    Optional<Delivery> findByTrackingNumberAndCustomerId(String trackingNumber, Long customerId);

    /**
     * Delivery search: optional status filter and optional text matched against the tracking number,
     * the receiver's name and the destination city. Pass null to skip a filter.
     */
    @Query(value = """
            select d from Delivery d
            where d.customer.id = :customerId
              and (:status is null or d.status = :status)
              and (:text is null
                   or lower(d.trackingNumber) like lower(concat('%', :text, '%'))
                   or exists (select 1 from DeliveryAddress a
                              where a.delivery = d and a.addressType = com.swiftdrop.backend.customer.entity.AddressType.DESTINATION
                                and (lower(a.contactName) like lower(concat('%', :text, '%'))
                                     or lower(a.city) like lower(concat('%', :text, '%')))))
            order by d.createdAt desc
            """)
    Page<Delivery> search(@Param("customerId") Long customerId,
                          @Param("status") DeliveryStatus status,
                          @Param("text") String text,
                          Pageable pageable);

    // ---- Dashboard ----

    /** How many of a customer's deliveries are in each status (statuses with none are absent). */
    @Query("select d.status as status, count(d) as total from Delivery d where d.customer.id = :customerId group by d.status")
    List<StatusCount> countByStatus(@Param("customerId") Long customerId);

    interface StatusCount {
        DeliveryStatus getStatus();

        long getTotal();
    }

    // ---- Public tracking (no login) ----

    /** By the unguessable public token. Return only non-private fields from this to the public API. */
    Optional<Delivery> findByPublicTrackingToken(String publicTrackingToken);

    Optional<Delivery> findByTrackingNumber(String trackingNumber);
}
