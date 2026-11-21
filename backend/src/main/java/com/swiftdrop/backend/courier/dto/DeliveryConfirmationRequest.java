package com.swiftdrop.backend.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload when confirming successful delivery with proof.
 */
public record DeliveryConfirmationRequest(
        @NotBlank(message = "Recipient signature/receiver name is required")
        @Size(max = 100, message = "Recipient sign name cannot exceed 100 characters")
        String recipientSignName,

        @Size(max = 50, message = "Proof type cannot exceed 50 characters")
        String proofType,

        @Size(max = 1000, message = "Delivery notes cannot exceed 1000 characters")
        String deliveryNotes,

        @Size(max = 2048, message = "Proof media URL cannot exceed 2048 characters")
        String proofMediaUrl,

        @Size(max = 200, message = "Location cannot exceed 200 characters")
        String location
) {
}

