package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Profile changes. Name, photo and email are changed in the login account (auth service), not here. */
public record UpdateCustomerProfileRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber,
        @Size(max = 150) String fullName) {
    public UpdateCustomerProfileRequest(String phoneNumber) { this(phoneNumber, null); }
}
