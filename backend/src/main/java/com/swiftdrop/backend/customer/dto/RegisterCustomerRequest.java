package com.swiftdrop.backend.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Registration details. Only what deliveries need: the name and login email already live in the customer's
 * account at the auth service, and the backend takes who they are from the access token.
 * The first address is required; it becomes the default. More addresses can be added later.
 */
public record RegisterCustomerRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phoneNumber,
        @NotNull(message = "Please add your address") @Valid CustomerAddressRequest address) {
}
