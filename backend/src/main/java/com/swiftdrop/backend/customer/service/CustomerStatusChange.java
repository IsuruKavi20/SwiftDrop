package com.swiftdrop.backend.customer.service;

/** Identifies the exact persisted status event to forward only after commit. */
public record CustomerStatusChange(Long deliveryId, Long trackingEventId) { }
