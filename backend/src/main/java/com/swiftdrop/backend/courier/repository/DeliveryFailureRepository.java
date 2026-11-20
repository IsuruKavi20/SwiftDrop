package com.swiftdrop.backend.courier.repository;

import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryFailureRepository extends JpaRepository<DeliveryFailure, Long> {

    /** History of delivery failure reports for a delivery, newest first. */
    @Query("SELECT f FROM DeliveryFailure f WHERE f.delivery.id = :deliveryId ORDER BY f.reportedAt DESC")
    List<DeliveryFailure> findByDeliveryIdOrderByReportedAtDesc(@Param("deliveryId") Long deliveryId);

    /** All failure reports submitted by a courier, newest first. */
    @Query("SELECT f FROM DeliveryFailure f WHERE f.courier.id = :courierId ORDER BY f.reportedAt DESC")
    List<DeliveryFailure> findByCourierIdOrderByReportedAtDesc(@Param("courierId") UUID courierId);
}
