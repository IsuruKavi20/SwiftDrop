package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for transitioning a delivery to an intermediate lifecycle status
 * (e.g. PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY).
 */
public record CourierStatusUpdateRequest(
        @NotNull(message = "Next status is required")
        DeliveryStatus nextStatus,

        @Size(max = 200, message = "Location cannot exceed 200 characters")
        String location,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes
) {
}

