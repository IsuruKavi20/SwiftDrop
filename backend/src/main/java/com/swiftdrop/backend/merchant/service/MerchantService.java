
package com.swiftdrop.backend.merchant.service;

import com.swiftdrop.backend.merchant.dto.MerchantUpdateRequest;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Transactional(readOnly = true)
    public Merchant getMerchantProfile(UUID authUserId) {
        return merchantRepository.findByAuthUserId(authUserId)
                .orElseThrow(() ->
                        new RuntimeException("Merchant profile not found"));
    }

    public Merchant updateMerchantProfile(
            UUID authUserId,
            MerchantUpdateRequest request
    ) {
        Merchant merchant = getMerchantProfile(authUserId);

        merchant.setBusinessName(request.businessName());
        merchant.setBusinessEmail(request.businessEmail());
        merchant.setContactNumber(request.contactNumber());
        merchant.setAddressLine1(request.addressLine1());
        merchant.setAddressLine2(request.addressLine2());
        merchant.setCity(request.city());
        merchant.setProvince(request.province());
        merchant.setPostalCode(request.postalCode());
        merchant.setCountry(request.country());

        return merchantRepository.save(merchant);
    }
}
