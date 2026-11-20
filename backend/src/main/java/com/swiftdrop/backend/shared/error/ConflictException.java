package com.swiftdrop.backend.shared.error;

/** The request clashes with the current state (already registered, already confirmed, ...) → 409 CONFLICT. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
