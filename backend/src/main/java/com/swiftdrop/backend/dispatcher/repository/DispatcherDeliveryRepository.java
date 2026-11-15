package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cross-customer order queries for the dispatcher module (234243J).
 * Only call from an authorized dispatcher/admin service; this repository does not enforce permissions.
 * Read lazy address, parcel and tracking collections within a service transaction.
 */
public interface DispatcherDeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByTrackingNumber(String trackingNumber);

    /**
     * Optional status and case-insensitive text filters. Text matches tracking numbers, courier names,
     * pickup/destination contacts and locations. Null/blank text lists all orders, newest first.
     * Address matching uses EXISTS so two matching addresses never duplicate a delivery or its page count.
     */
    default Page<Delivery> search(DeliveryStatus status, String text, Pageable pageable) {
        return searchByPattern(status, likePattern(text), pageable);
    }

    /** "%" for no text, otherwise a lower-case contains-pattern that takes %, _ and backslashes literally. */
    static String likePattern(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        return "%" + text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Confirmed deliveries with no courier yet, oldest first (they have waited longest). Same text matching as search. */
    default Page<Delivery> searchUnassigned(String text, Pageable pageable) {
        return searchUnassignedByPattern(DeliveryStatus.CONFIRMED, likePattern(text), pageable);
    }

    /** Internal query entry point: use searchUnassigned(...) for literal user input. */
    @Query("""
            select d from Delivery d
            where d.status = :status and d.courier is null
              and (lower(d.trackingNumber) like :pattern escape '\\'
                   or exists (select 1 from DeliveryAddress a where a.delivery = d
                              and (lower(a.contactName) like :pattern escape '\\'
                                   or lower(a.street) like :pattern escape '\\'
                                   or lower(a.city) like :pattern escape '\\'
                                   or lower(a.district) like :pattern escape '\\')))
            order by d.createdAt asc, d.id asc
            """)
    Page<Delivery> searchUnassignedByPattern(@Param("status") DeliveryStatus status,
                                             @Param("pattern") String pattern, Pageable pageable);

    long countByStatusAndCourierIsNull(DeliveryStatus status);

    /** Internal query entry point: use search(...) for literal user input and non-null patterns. */
    @Query("""
            select d from Delivery d
            left join d.courier c
            where (:status is null or d.status = :status)
              and (lower(d.trackingNumber) like :pattern escape '\\'
                   or lower(c.name) like :pattern escape '\\'
                   or exists (select 1 from DeliveryAddress a where a.delivery = d
                              and (lower(a.contactName) like :pattern escape '\\'
                                   or lower(a.street) like :pattern escape '\\'
                                   or lower(a.city) like :pattern escape '\\'
                                   or lower(a.district) like :pattern escape '\\')))
            order by d.createdAt desc, d.id desc
            """)
    Page<Delivery> searchByPattern(@Param("status") DeliveryStatus status,
                                   @Param("pattern") String pattern, Pageable pageable);

    /** Global dashboard counts. Missing statuses have zero deliveries; group them into cards in the service. */
    @Query("select d.status as status, count(d) as total from Delivery d group by d.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        DeliveryStatus getStatus();
        long getTotal();
    }

    /** Call within a write transaction before assigning/reassigning; serializes writes to this delivery. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);
}
