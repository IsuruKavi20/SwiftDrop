package com.swiftdrop.backend.merchant.repository;

import com.swiftdrop.backend.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    /** The business owned by this person (their token's "sub"). */
    Optional<Merchant> findByOwnerId(UUID ownerId);
}
