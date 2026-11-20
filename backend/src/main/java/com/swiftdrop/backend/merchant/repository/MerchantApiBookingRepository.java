package com.swiftdrop.backend.merchant.repository;

import com.swiftdrop.backend.merchant.entity.MerchantApiBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantApiBookingRepository extends JpaRepository<MerchantApiBooking, Long> {
    Optional<MerchantApiBooking> findByMerchantIdAndClientIdAndIdempotencyKey(Long merchantId, String clientId,
                                                                             String idempotencyKey);
}
