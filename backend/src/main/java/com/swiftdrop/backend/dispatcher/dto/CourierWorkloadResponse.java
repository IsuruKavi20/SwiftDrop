package com.swiftdrop.backend.dispatcher.dto;

import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;

import java.util.List;
import java.util.UUID;

/**
 * A courier profile with its current workload: active assignments only, their tracking numbers oldest first.
 * {@code userId} is the person's auth id; {@code accountActive} and {@code courierRole} come from the local copy of
 * the auth service (false once the account is blocked or the Courier role is removed).
 */
public record CourierWorkloadResponse(
        Long courierId,
        String name,
        String phoneNumber,
        String vehicleType,
        String vehicleNumber,
        CourierAvailability availability,
        int activeDeliveries,
        List<String> currentTrackingNumbers,
        UUID userId,
        boolean accountActive,
        boolean courierRole) { }
