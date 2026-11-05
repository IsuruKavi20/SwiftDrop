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
}
