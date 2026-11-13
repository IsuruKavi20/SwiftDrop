package com.swiftdrop.backend.merchant.repository;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Deliveries owned by merchants in the Merchant Module.
 * Enforces merchant-level scoping and provides paginated/filtered queries.
 */
public interface MerchantDeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByTrackingNumber(String trackingNumber);

    Page<Delivery> findByMerchantIdOrderByCreatedAtDescIdDesc(Long merchantId, Pageable pageable);

    Page<Delivery> findByMerchantIdAndStatusOrderByCreatedAtDescIdDesc(Long merchantId, DeliveryStatus status, Pageable pageable);

    Optional<Delivery> findByIdAndMerchantId(Long id, Long merchantId);

    @Query("select d.status as status, count(d) as total from Delivery d where d.merchant.id = :merchantId group by d.status")
    List<StatusCount> countByStatus(@Param("merchantId") Long merchantId);

    interface StatusCount {
        DeliveryStatus getStatus();
        long getTotal();
    }
}
