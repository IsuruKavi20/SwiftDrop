package com.swiftdrop.backend.customer.dto;

/** Validation rules shared by several request DTOs. */
final class ValidationPatterns {

    /** 9 to 15 digits, optional leading +, e.g. 0771234567 or +94771234567. */
    static final String PHONE = "^\\+?[0-9]{9,15}$";
    static final String PHONE_MESSAGE = "must be 9 to 15 digits, optionally starting with +";

    private ValidationPatterns() {
    }
}
