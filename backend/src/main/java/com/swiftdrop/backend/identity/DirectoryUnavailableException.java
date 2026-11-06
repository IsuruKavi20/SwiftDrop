package com.swiftdrop.backend.identity;

/** The auth service's Directory API can't be reached, refused us, or isn't configured. */
public class DirectoryUnavailableException extends RuntimeException {

    public DirectoryUnavailableException(String message) {
        super(message);
    }

    public DirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
