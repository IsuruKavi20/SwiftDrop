package com.swiftdrop.backend.identity;

import java.util.UUID;

/** No such person in the auth service, or the person can't be used (blocked, disabled or deleted). */
public class UnknownUserException extends RuntimeException {

    public UnknownUserException(UUID id) {
        super("No usable user with id " + id);
    }
}
