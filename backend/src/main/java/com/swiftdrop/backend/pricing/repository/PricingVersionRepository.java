package com.swiftdrop.backend.pricing.repository;

import com.swiftdrop.backend.pricing.entity.PricingVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingVersionRepository extends JpaRepository<PricingVersion, Long> {

    /** The published tariff quotes use: the highest version. */
    @EntityGraph(attributePaths = "publishedBy")
    Optional<PricingVersion> findTopByOrderByVersionDesc();
}
