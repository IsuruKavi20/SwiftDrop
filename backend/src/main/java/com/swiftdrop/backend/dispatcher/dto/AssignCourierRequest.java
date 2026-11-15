package com.swiftdrop.backend.dispatcher.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Assign a courier (their courier profile id) to a delivery. When the delivery already has a courier this is a
 * reassignment and {@code reason} is required: it is stored as the ended assignment's end reason.
 */
public record AssignCourierRequest(
        @NotNull(message = "Choose a courier") Long courierId,
        @Size(max = 500, message = "Keep the reason under 500 characters") String reason) { }
