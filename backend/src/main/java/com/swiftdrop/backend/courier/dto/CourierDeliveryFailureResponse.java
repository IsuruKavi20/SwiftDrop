package com.swiftdrop.backend.courier.dto;

import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;

import java.time.Instant;

/**
 * Delivery failure audit report details.
 */
public record CourierDeliveryFailureResponse(
        Long id,
        DeliveryFailureReason failureReason,
        String failureRemarks,
        Instant reportedAt
) {

    public static CourierDeliveryFailureResponse from(DeliveryFailure failure) {
        if (failure == null) {
            return null;
        }
        return new CourierDeliveryFailureResponse(
                failure.getId(),
                failure.getFailureReason(),
                failure.getFailureRemarks(),
                failure.getReportedAt()
        );
    }
}

