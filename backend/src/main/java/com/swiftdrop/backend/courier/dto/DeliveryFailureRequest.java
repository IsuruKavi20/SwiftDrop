package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload when recording an unsuccessful delivery attempt.
 */
public record DeliveryFailureRequest(
        @NotNull(message = "Failure reason is required")
        DeliveryFailureReason failureReason,

        @NotBlank(message = "Failure remarks are required")
        @Size(max = 1000, message = "Failure remarks cannot exceed 1000 characters")
        String failureRemarks,

        @Size(max = 200, message = "Location cannot exceed 200 characters")
        String location
) {
}

