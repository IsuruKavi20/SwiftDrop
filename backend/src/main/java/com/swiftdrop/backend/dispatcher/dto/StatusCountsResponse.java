package com.swiftdrop.backend.dispatcher.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;

import java.util.Map;

/** Deliveries per status across all customers and merchants (every status present, 0 when none), plus how many
 * confirmed deliveries still wait for a courier. */
public record StatusCountsResponse(Map<DeliveryStatus, Long> statuses, long unassigned) { }
