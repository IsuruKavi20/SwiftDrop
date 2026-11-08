package com.swiftdrop.backend.identity.security;

/**
 * The SET can't be trusted or read (bad signature, wrong type, issuer or audience, missing parts). The receiver answers
 * 400, which tells the auth service never to send this one again; so this must ONLY be thrown for a truly invalid SET.
 */
public class InvalidSetException extends RuntimeException {

    public InvalidSetException(String description) {
        super(description);
    }

    InvalidSetException(String description, Throwable cause) {
        super(description, cause);
    }
}
