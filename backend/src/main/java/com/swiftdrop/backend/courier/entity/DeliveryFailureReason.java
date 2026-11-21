package com.swiftdrop.backend.courier.entity;

/**
 * Standardized reasons when a delivery cannot be completed successfully.
 */
public enum DeliveryFailureReason {
    RECIPIENT_UNAVAILABLE,
    INCORRECT_ADDRESS,
    CUSTOMER_REJECTED,
    DAMAGED_PARCEL,
    ACCESS_RESTRICTED,
    WEATHER_DELAY,
    OTHER
}
