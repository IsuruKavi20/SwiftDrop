package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.TrackingEvent;

import java.time.Instant;

/** One timeline entry. Leaves out who recorded it (an internal user id). */
public record TrackingEventResponse(
        DeliveryStatus status,
        String description,
        String locationName,
        Instant eventTime) {

    public static TrackingEventResponse from(TrackingEvent event) {
        return new TrackingEventResponse(event.getStatus(), event.getDescription(), event.getLocationName(), event.getEventTime());
    }
}
