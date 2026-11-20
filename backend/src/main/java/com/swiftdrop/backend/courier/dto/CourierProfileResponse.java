package com.swiftdrop.backend.courier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.identity.entity.AppUser;

import java.util.UUID;

/**
 * Courier personnel profile information.
 */
public record CourierProfileResponse(
        UUID userId,
        String fullName,
        String email,
        String phoneNumber,
        String vehicleType,
        String assignedZone,
        @JsonProperty("isActive")
        boolean isActive,
        String avatarUrl
) {

    public static CourierProfileResponse from(CourierProfile profile, AppUser user, String email) {
        return new CourierProfileResponse(
                user.getId(),
                user.getName().orElse(null),
                email,
                profile != null ? profile.getPhoneNumber() : null,
                profile != null ? profile.getVehicleType() : null,
                profile != null ? profile.getAssignedZone() : null,
                profile != null ? profile.isActive() : user.isActive(),
                user.getPictureUrl().orElse(null)
        );
    }
}
