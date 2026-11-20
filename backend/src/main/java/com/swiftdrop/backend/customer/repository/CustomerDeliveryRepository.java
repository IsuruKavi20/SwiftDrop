package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
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
// Known shortcut (docs/MODULES.md): notification reads this directly. To be replaced by a small API or events;
// listed here so the module guard allows only this, not the rest of the package.
@org.springframework.modulith.NamedInterface("shortcut-CustomerDeliveryRepository")
public interface CustomerDeliveryRepository extends JpaRepository<Delivery, Long> {

    // ---- Booking ----

    boolean existsByTrackingNumber(String trackingNumber);

    // ---- My Deliveries / delivery details ----

    /** All of a customer's deliveries, newest first. */
    Page<Delivery> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /** One delivery, only if it belongs to this customer. */
    Optional<Delivery> findByIdAndCustomerId(Long id, Long customerId);

    /** Lock a booking while cancelling it to avoid racing courier assignment or status changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :deliveryId and d.customer.id = :customerId")
    Optional<Delivery> lockCustomerDelivery(@Param("deliveryId") Long deliveryId, @Param("customerId") Long customerId);

    /** Track Parcel for a logged-in customer: only their own delivery. */
    Optional<Delivery> findByTrackingNumberAndCustomerId(String trackingNumber, Long customerId);

    /**
     * Delivery search: optional status filter and optional text matched (case-insensitively, anywhere) against
     * the tracking number, the receiver's name and the destination city. Pass null or blank to skip a filter.
     */
    default Page<Delivery> search(Long customerId, DeliveryStatus status, String text, Pageable pageable) {
        return searchByPattern(customerId, status, containsPattern(text), pageable);
    }

    /**
     * Never binds a null text: PostgreSQL cannot type a null parameter inside lower() and fails with
     * "function lower(bytea) does not exist". No text becomes the pattern "%", which matches everything.
     */
    @Query(value = """
            select d from Delivery d
            where d.customer.id = :customerId
              and (:status is null or d.status = :status)
              and (lower(d.trackingNumber) like :pattern escape '\\'
                   or exists (select 1 from DeliveryAddress a
                              where a.delivery = d and a.addressType = com.swiftdrop.backend.customer.entity.AddressType.DESTINATION
                                and (lower(a.contactName) like :pattern escape '\\'
                                     or lower(a.city) like :pattern escape '\\')))
            order by d.createdAt desc
            """)
    Page<Delivery> searchByPattern(@Param("customerId") Long customerId,
                                   @Param("status") DeliveryStatus status,
                                   @Param("pattern") String pattern,
                                   Pageable pageable);

    /** "Kan_dy" becomes "%kan\_dy%": the user's % and _ are matched literally, not as wildcards. */
    static String containsPattern(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        String escaped = text.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

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
