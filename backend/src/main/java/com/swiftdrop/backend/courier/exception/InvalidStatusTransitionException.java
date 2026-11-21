package com.swiftdrop.backend.courier.exception;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an unpermitted status lifecycle transition is attempted (HTTP 409 Conflict).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStatusTransitionException extends RuntimeException {

    private final DeliveryStatus currentStatus;
    private final DeliveryStatus attemptedStatus;

    public InvalidStatusTransitionException(String trackingNumber, DeliveryStatus currentStatus, DeliveryStatus attemptedStatus) {
        super(String.format("Cannot transition delivery '%s' from %s to %s", trackingNumber, currentStatus, attemptedStatus));
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public DeliveryStatus getCurrentStatus() {
        return currentStatus;
    }

    public DeliveryStatus getAttemptedStatus() {
        return attemptedStatus;
    }
}
