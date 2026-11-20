package com.swiftdrop.backend.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Pickup (sender) or destination (receiver) details in a merchant booking. */
public record DeliveryAddressRequest(
        @NotBlank @Size(max = 100) String contactName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "must be 9 to 15 digits, optionally starting with +") String contactPhone,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district,
        @Size(max = 15) String postalCode) {
}
