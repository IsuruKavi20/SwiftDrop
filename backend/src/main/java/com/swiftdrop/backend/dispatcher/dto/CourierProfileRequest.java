package com.swiftdrop.backend.dispatcher.dto;

import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create (POST, {@code userId} required: the auth id of a person with the Courier role) or edit (PUT, {@code userId}
 * ignored) a courier's work profile. Blank vehicle fields are stored as empty (null).
 */
public record CourierProfileRequest(
        UUID userId,
        @NotBlank(message = "Enter a phone number")
        @Size(max = 20, message = "Enter a phone number of 9 to 20 characters")
        @Pattern(regexp = "^\\s*\\+?[0-9][0-9 ]{8,}\\s*$", message = "Enter a phone number of 9 to 20 characters")
        String phoneNumber,
        @Size(max = 30, message = "Vehicle type is at most 30 characters")
        String vehicleType,
        @Size(max = 30, message = "Vehicle number is at most 30 characters")
        String vehicleNumber,
        @NotNull(message = "Choose an availability")
        CourierAvailability availability) { }
