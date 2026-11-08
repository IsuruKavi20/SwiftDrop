package com.swiftdrop.backend.merchant.service;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.merchant.dto.MerchantUpdateRequest;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The merchant's business profile. No separate registration: an approved merchant's profile is created empty on
 * their first visit, then they complete it.
 */
@Service
public class MerchantService {

    private final MerchantRepository merchants;

    public MerchantService(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    /** The owner's profile, creating an empty one the first time. */
    @Transactional
    public Merchant profileOf(AppUser owner) {
        return merchants.findByOwnerId(owner.getId()).orElseGet(() -> createFor(owner));
    }

    @Transactional
    public Merchant updateProfile(AppUser owner, MerchantUpdateRequest request) {
        Merchant merchant = profileOf(owner);
        merchant.updateDetails(request.businessName().trim(), request.businessEmail().trim(),
                request.contactNumber().trim(), request.addressLine1().trim(), blankToNull(request.addressLine2()),
                request.city().trim(), blankToNull(request.province()), blankToNull(request.postalCode()),
                request.country().trim());
        return merchants.saveAndFlush(merchant);
    }

    // Two first requests at the very same moment: the unique owner column lets only one insert win; the other
    // request fails once and its retry finds the profile
    private Merchant createFor(AppUser owner) {
        return merchants.saveAndFlush(new Merchant(owner));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
