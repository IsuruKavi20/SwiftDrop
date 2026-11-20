package com.swiftdrop.backend.customer.exception;

/** The request clashes with the current state: already registered, email taken, booking already confirmed, ... (409). */
public class ConflictException extends com.swiftdrop.backend.shared.error.ConflictException {

    public ConflictException(String message) {
        super(message);
    }
}
