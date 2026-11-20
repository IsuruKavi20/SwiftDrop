package com.swiftdrop.backend.customer.exception;

/**
 * The thing asked for does not exist, or does not belong to the caller. Both give the same answer (404), so a
 * customer cannot find out whether someone else's delivery or address exists.
 */
public class NotFoundException extends com.swiftdrop.backend.shared.error.NotFoundException {

    public NotFoundException(String message) {
        super(message);
    }
}
