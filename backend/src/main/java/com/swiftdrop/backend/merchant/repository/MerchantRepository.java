package com.swiftdrop.backend.merchant.repository;

import com.swiftdrop.backend.merchant.entity.Merchant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    /** The business owned by this person (their token's "sub"). */
    Optional<Merchant> findByOwnerId(UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Merchant m where m.owner.id = :ownerId")
    Optional<Merchant> lockByOwnerId(@Param("ownerId") UUID ownerId);
}
