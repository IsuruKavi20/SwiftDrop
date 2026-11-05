package com.swiftdrop.backend.customer.entity;

/** Where a delivery is in its lifecycle. Used both as the delivery's current status and on each tracking event. */
public enum DeliveryStatus {
    PENDING,           // booked, not yet confirmed by the customer
    CONFIRMED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED
}
