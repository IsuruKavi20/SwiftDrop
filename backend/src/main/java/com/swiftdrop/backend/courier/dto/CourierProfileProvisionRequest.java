package com.swiftdrop.backend.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for provisioning or updating a courier operational profile.
 * Operational attributes (vehicle, zone, active status) are maintained here under
 * Dispatcher/Admin supervision while identity belongs to {@link com.swiftdrop.backend.identity.entity.AppUser}.
 */
public record CourierProfileProvisionRequest(
        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        String phoneNumber,

        @NotBlank(message = "Vehicle type is required")
        @Size(max = 50, message = "Vehicle type must not exceed 50 characters")
        String vehicleType,

        @Size(max = 100, message = "Assigned zone must not exceed 100 characters")
        String assignedZone,

        Boolean active
) {
    public CourierProfileProvisionRequest(String phoneNumber, String vehicleType, String assignedZone) {
        this(phoneNumber, vehicleType, assignedZone, null);
    }
}
