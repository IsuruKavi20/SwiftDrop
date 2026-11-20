package com.swiftdrop.backend.customer.service;

/**
 * Event: "a delivery's status changed". Published by customer after the change is saved; any module may listen
 * (notification sends messages). Customer does not know who listens.
 */
@org.springframework.modulith.NamedInterface("events")
public record CustomerStatusChange(Long deliveryId, Long trackingEventId) { }
