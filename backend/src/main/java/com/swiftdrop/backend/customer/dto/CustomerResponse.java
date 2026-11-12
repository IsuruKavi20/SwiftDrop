package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Customer;

import java.time.Instant;

/**
 * The customer's profile. {@code name} is the full name they entered (or, for old profiles without one, the login
 * account's name); the photo comes from their login account (auth service).
 */
public record CustomerResponse(
        Long id,
        String name,
        String email,
        String pictureUrl,
        String phoneNumber,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        String fullName = customer.getFullName();
        String name = fullName != null && !fullName.isBlank() ? fullName : customer.getUser().getName().orElse(null);
        return new CustomerResponse(customer.getId(), name, customer.getEmail(),
                customer.getUser().getPictureUrl().orElse(null), customer.getPhoneNumber(), customer.getCreatedAt());
    }
}
