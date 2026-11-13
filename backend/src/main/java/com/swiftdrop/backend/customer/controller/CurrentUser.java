package com.swiftdrop.backend.customer.controller;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Who is calling: the user id from the access token ("sub"), never from the URL or request body. */
final class CurrentUser {

    private CurrentUser() {
    }

    static UUID id(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    /** The login email from the token's "email" claim, or null if the token has none. */
    static String email(Jwt token) {
        String email = token.getClaimAsString("email");
        return email == null || email.isBlank() ? null : email.trim();
    }
}
