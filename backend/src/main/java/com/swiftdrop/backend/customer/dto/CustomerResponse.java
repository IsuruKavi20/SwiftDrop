package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.Customer;

import java.time.Instant;

/** Customer-editable display name, login email where available, and contact details. */
public record CustomerResponse(
        Long id,
        String name,
        String pictureUrl,
        String email,
        String phoneNumber,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(),
                customer.getFullName() != null ? customer.getFullName() : customer.getUser().getName().orElse(null),
                customer.getUser().getPictureUrl().orElse(null), customer.getEmail(),
                customer.getPhoneNumber(), customer.getCreatedAt());
    }
}
