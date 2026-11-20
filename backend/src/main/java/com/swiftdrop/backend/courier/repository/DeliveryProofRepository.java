package com.swiftdrop.backend.courier.repository;

import com.swiftdrop.backend.courier.entity.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, Long> {

    /** Finds the delivery proof for a specific delivery. */
    @Query("SELECT p FROM DeliveryProof p WHERE p.delivery.id = :deliveryId")
    Optional<DeliveryProof> findByDeliveryId(@Param("deliveryId") Long deliveryId);

    @Query("SELECT count(p) > 0 FROM DeliveryProof p WHERE p.delivery.id = :deliveryId")
    boolean existsByDeliveryId(@Param("deliveryId") Long deliveryId);

    /** All proofs recorded by a specific courier. */
    @Query("SELECT p FROM DeliveryProof p WHERE p.courier.id = :courierId")
    List<DeliveryProof> findByCourierId(@Param("courierId") UUID courierId);
}
