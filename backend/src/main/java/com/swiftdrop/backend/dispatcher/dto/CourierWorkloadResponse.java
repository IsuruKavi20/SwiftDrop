package com.swiftdrop.backend.dispatcher.dto;

import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;

import java.util.List;

/** A courier profile with its current workload: active assignments only, their tracking numbers oldest first. */
public record CourierWorkloadResponse(
        Long courierId,
        String name,
        String phoneNumber,
        String vehicleType,
        String vehicleNumber,
        CourierAvailability availability,
        int activeDeliveries,
        List<String> currentTrackingNumbers) { }
