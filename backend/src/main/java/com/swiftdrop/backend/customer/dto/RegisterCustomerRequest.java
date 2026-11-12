package com.swiftdrop.backend.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration details: the customer's full name (shown on their dashboard) and phone. The login email lives in
 * their account at the auth service, and the backend takes who they are from the access token.
 */
public record RegisterCustomerRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber) {
}
