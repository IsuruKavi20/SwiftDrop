package com.swiftdrop.backend.courier.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * Thrown when a courier attempts to access or modify a delivery assigned to someone else (HTTP 403 Forbidden).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedDeliveryAccessException extends RuntimeException {

    public UnauthorizedDeliveryAccessException(String trackingNumber, UUID courierId) {
        super(String.format("Courier '%s' is not authorized to access or update delivery '%s'", courierId, trackingNumber));
    }
}
