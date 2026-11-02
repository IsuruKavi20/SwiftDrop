package com.swiftdrop.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Where our users' tokens come from: the SwiftDrop auth service ({@code swiftdrop.auth.*}).
 *
 * @param issuer         who must have issued the token ("iss" claim)
 * @param jwkSetUri      the auth service's public keys, used to check signatures (default: issuer + /.well-known/jwks.json)
 * @param audience       who the token must be meant for ("aud" claim)
 * @param allowedOrigins browser apps allowed to call this API (CORS); empty = none
 */
@ConfigurationProperties("swiftdrop.auth")
public record SwiftDropAuthProperties(String issuer, String jwkSetUri, String audience, List<String> allowedOrigins) {

    public SwiftDropAuthProperties {
        if (issuer == null || !issuer.startsWith("https://")) {
            throw new IllegalArgumentException("swiftdrop.auth.issuer must be the https address of the auth service");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("swiftdrop.auth.audience is required");
        }
        jwkSetUri = jwkSetUri == null || jwkSetUri.isBlank() ? issuer + "/.well-known/jwks.json" : jwkSetUri;
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
