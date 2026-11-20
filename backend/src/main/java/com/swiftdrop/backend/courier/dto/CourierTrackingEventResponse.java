package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.TrackingEvent;

import java.time.Instant;

/**
 * Audit timeline step in a delivery's history.
 */
public record CourierTrackingEventResponse(
        Long id,
        DeliveryStatus status,
        String location,
        String description,
        Instant timestamp,
        String recordedByName
) {

    public static CourierTrackingEventResponse from(TrackingEvent event) {
        String recorder = (event.getRecordedBy() != null && event.getRecordedBy().getName().isPresent())
                ? event.getRecordedBy().getName().get()
                : "System";
        return new CourierTrackingEventResponse(
                event.getId(),
                event.getStatus(),
                event.getLocationName(),
                event.getDescription(),
                event.getEventTime(),
                recorder
        );
    }
}
