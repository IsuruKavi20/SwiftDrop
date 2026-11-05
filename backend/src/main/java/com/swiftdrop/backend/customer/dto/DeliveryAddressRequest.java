package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Pickup (sender) or destination (receiver) details in a booking. */
public record DeliveryAddressRequest(
        @NotBlank @Size(max = 100) String contactName,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String contactPhone,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district,
        @Size(max = 15) String postalCode) {
}
