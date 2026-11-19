package com.swiftdrop.backend.merchant.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter for an agreed issuer contract. Only receives JWTs verified by Spring Security.
 * A production adapter must verify the Merchant machine type, booking permission, stable client ID,
 * authoritative business ownership, and the issuer's revocation and suspension rules.
 */
public interface MerchantMachineTokenContract {
    /** Recognizes machine tokens even when they lack booking permission. */
    boolean isMachine(Jwt token);

    Optional<BookingIdentity> authorizeBooking(Jwt token);

    record BookingIdentity(UUID ownerId, String clientId) {
        public BookingIdentity {
            if (ownerId == null || clientId == null || clientId.isBlank() || clientId.length() > 255) {
                throw new IllegalArgumentException("Invalid machine booking identity");
            }
        }
    }
}
