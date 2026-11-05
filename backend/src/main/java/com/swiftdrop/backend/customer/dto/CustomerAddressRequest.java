package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create or edit a saved address. */
public record CustomerAddressRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district,
        @Size(max = 15) String postalCode,
        boolean defaultAddress) {
}
