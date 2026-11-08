package com.swiftdrop.backend.dispatcher.entity;

/** Operational availability; account activation and roles remain in the auth service. */
public enum CourierAvailability {
    AVAILABLE,
    BUSY,
    OFFLINE
}
