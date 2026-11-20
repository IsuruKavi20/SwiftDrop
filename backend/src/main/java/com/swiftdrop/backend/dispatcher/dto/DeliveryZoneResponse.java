package com.swiftdrop.backend.dispatcher.dto;

import java.util.List;

/** A zone with its districts in the usual province order (Western: Colombo, Gampaha, Kalutara; Central: …). */
public record DeliveryZoneResponse(Long id, String name, String code, List<String> districts, boolean active) { }
