package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.courier.entity.DeliveryProof;

import java.time.Instant;

/**
 * Proof of delivery details returned to courier detail view.
 */
public record CourierDeliveryProofResponse(
        Long id,
        String proofType,
        String recipientSignName,
        String deliveryNotes,
        String proofMediaUrl,
        Instant capturedAt
) {

    public static CourierDeliveryProofResponse from(DeliveryProof proof) {
        if (proof == null) {
            return null;
        }
        return new CourierDeliveryProofResponse(
                proof.getId(),
                proof.getProofType(),
                proof.getRecipientSignName(),
                proof.getDeliveryNotes(),
                proof.getProofMediaUrl(),
                proof.getCapturedAt()
        );
    }
}

