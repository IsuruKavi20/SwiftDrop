package com.swiftdrop.backend.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The thing asked for does not exist, or does not belong to the caller. Both give the same answer (404), so a
 * customer cannot find out whether someone else's delivery or address exists.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
