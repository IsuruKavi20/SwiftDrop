package com.swiftdrop.backend.merchant.dto;

import com.swiftdrop.backend.merchant.entity.Merchant;

import java.time.Instant;

/**
 * The merchant's business profile. {@code complete} is false until the business details are filled in;
 * {@code ownerName} comes from the owner's login account.
 */
public record MerchantResponse(
        Long id,
        boolean complete,
        String ownerName,
        String businessName,
        String businessEmail,
        String contactNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String country,
        Instant createdAt,
        Instant updatedAt
) {

    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.isComplete(),
                merchant.getOwner().getName().orElse(null),
                merchant.getBusinessName(),
                merchant.getBusinessEmail(),
                merchant.getContactNumber(),
                merchant.getAddressLine1(),
                merchant.getAddressLine2(),
                merchant.getCity(),
                merchant.getProvince(),
                merchant.getPostalCode(),
                merchant.getCountry(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt()
        );
    }
}
