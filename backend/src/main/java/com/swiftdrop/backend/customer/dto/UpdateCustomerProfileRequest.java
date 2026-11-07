package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Profile changes. The phone number is required; first name, last name and email are optional and only change
 * when sent (send them once to fill in a profile that was registered before these fields were stored).
 */
public record UpdateCustomerProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber) {

    /** Only the phone number, keeping the stored name and email. */
    public UpdateCustomerProfileRequest(String phoneNumber) {
        this(null, null, null, phoneNumber);
    }
}
