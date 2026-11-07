package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Customer;

import java.time.Instant;

/** The customer's profile: the details they registered with, plus the photo from their login account. */
public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String pictureUrl,
        String phoneNumber,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getUser().getPictureUrl().orElse(null), customer.getPhoneNumber(),
                customer.getCreatedAt());
    }
}
