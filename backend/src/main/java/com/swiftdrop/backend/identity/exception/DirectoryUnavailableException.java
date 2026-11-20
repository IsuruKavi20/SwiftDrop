package com.swiftdrop.backend.identity.exception;

/** The auth service's Directory API can't be reached, refused us, or isn't configured. */
public class DirectoryUnavailableException extends com.swiftdrop.backend.shared.error.ServiceUnavailableException {

    public DirectoryUnavailableException(String message) {
        super(message);
    }

    public DirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
