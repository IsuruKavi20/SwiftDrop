package com.swiftdrop.backend.merchant.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.TrackingEvent;

import java.time.Instant;

/** One timeline entry for a merchant order. */
public record TrackingEventResponse(
        DeliveryStatus status,
        String description,
        String locationName,
        Instant eventTime) {

    public static TrackingEventResponse from(TrackingEvent event) {
        return new TrackingEventResponse(event.getStatus(), event.getDescription(), event.getLocationName(), event.getEventTime());
    }
}
