package com.swiftdrop.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A token is trusted only if WE issued it, FOR the SwiftDrop API, and it hasn't expired. */
class TokenValidationTest {

    private static final String ISSUER = "https://auth.getswiftdrop.me";
    private final OAuth2TokenValidator<Jwt> validator =
            SecurityConfig.tokenValidator(new SwiftDropAuthProperties(ISSUER, null, "swiftdrop-api", List.of()));

    @Test
    void aTokenFromTheSwiftDropAuthServiceForOurApiIsAccepted() {
        assertThat(validator.validate(token(ISSUER, "swiftdrop-api", Instant.now().plusSeconds(600))).hasErrors()).isFalse();
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        assertThat(validator.validate(token("https://evil.example", "swiftdrop-api", Instant.now().plusSeconds(600))).hasErrors()).isTrue();
    }

    @Test
    void aTokenMeantForAnotherApiIsRejected() {
        assertThat(validator.validate(token(ISSUER, "some-other-api", Instant.now().plusSeconds(600))).hasErrors()).isTrue();
    }

    @Test
    void anExpiredTokenIsRejected() {
        assertThat(validator.validate(token(ISSUER, "swiftdrop-api", Instant.now().minusSeconds(600))).hasErrors()).isTrue();
    }

    @Test
    void rolesAndEntitlementsBecomeAuthorities() {
        Jwt jwt = token(ISSUER, "swiftdrop-api", Instant.now().plusSeconds(600));
        List<String> authorities = SecurityConfig.authorities(jwt).stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_COURIER", "delivery:read-own");
    }

    private static Jwt token(String issuer, String audience, Instant expiresAt) {
        // Exactly the headers the auth service sends: an RFC 9068 access token ("at+jwt"), not a plain "JWT"
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("0b4e883e-91af-4f24-93bf-2cc1bbbcb19f")
                .claim("client_id", "swiftdrop-web")
                .jti("de215567-b982-4855-94a4-5a575085f896")
                .claim("roles", List.of("COURIER"))
                .claim("entitlements", List.of("delivery:read-own"))
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .build();
    }
}
