package com.swiftdrop.backend.shared.error;

/**
 * The thing asked for does not exist, or does not belong to the caller → 404 NOT_FOUND. Both cases give the same
 * answer, so nobody can find out whether someone else's data exists. Any module can throw it (or a subclass).
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
