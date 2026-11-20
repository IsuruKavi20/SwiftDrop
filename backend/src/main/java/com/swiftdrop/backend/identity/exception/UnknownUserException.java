package com.swiftdrop.backend.identity.exception;

import java.util.UUID;

/** No such person in the auth service, or the person can't be used (blocked, disabled or deleted). */
// A 404 for the caller: the id is not repeated in the answer
public class UnknownUserException extends com.swiftdrop.backend.shared.error.NotFoundException {

    public UnknownUserException(UUID id) {
        super("This person was not found");
    }
}
