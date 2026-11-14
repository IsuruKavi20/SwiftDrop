package com.swiftdrop.backend.shared.error;

/** A service we depend on (e.g. the auth service) can't be reached → 503 SERVICE_UNAVAILABLE. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
