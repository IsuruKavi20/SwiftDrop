package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Read side of the tracking timeline. Record new events through Delivery.addTrackingEvent so the status stays in step. */
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    /** The tracking timeline, oldest first. */
    List<TrackingEvent> findByDeliveryIdOrderByEventTimeAsc(Long deliveryId);

    /** The most recent event, e.g. for "last seen at" on the dashboard. */
    Optional<TrackingEvent> findFirstByDeliveryIdOrderByEventTimeDesc(Long deliveryId);
}
