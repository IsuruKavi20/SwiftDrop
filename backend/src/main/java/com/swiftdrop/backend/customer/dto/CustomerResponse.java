package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Customer;

import java.time.Instant;

/** The customer's profile. Name and photo come from their login account (auth service); phone is ours. */
public record CustomerResponse(
        Long id,
        String name,
        String pictureUrl,
        String phoneNumber,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getUser().getName().orElse(null),
                customer.getUser().getPictureUrl().orElse(null), customer.getPhoneNumber(), customer.getCreatedAt());
    }
}
