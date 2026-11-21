package com.swiftdrop.backend.merchant.security;

import com.swiftdrop.backend.identity.client.MerchantTokenIntrospection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "swiftdrop.merchant.machine-booking.enabled", havingValue = "true")
public class AuthMerchantMachineTokenContract implements MerchantMachineTokenContract {
    private static final String CLIENT_PREFIX = "mc_";
    private static final int CLIENT_ID_LENGTH = CLIENT_PREFIX.length() + 22;

    private final MerchantTokenIntrospection introspection;

    public AuthMerchantMachineTokenContract(MerchantTokenIntrospection introspection) {
        this.introspection = introspection;
    }

    @Override
    public boolean isMachine(Jwt token) {
        return stringClaim(token, "sub").startsWith(CLIENT_PREFIX);
    }

    @Override
    public Optional<BookingIdentity> authorizeBooking(Jwt token) {
        String clientId = stringClaim(token, "client_id");
        Object permissions = token.getClaims().get("entitlements");
        UUID owner = ownerId(stringClaim(token, "merchant_owner"));
        if (!isMachine(token) || clientId.length() != CLIENT_ID_LENGTH || !clientId.equals(token.getSubject())
                || owner == null || !(permissions instanceof List<?> entitlements)
                || !entitlements.containsAll(List.of("parcel:create", "merchant:api-access"))) {
            return Optional.empty();
        }

        var decision = introspection.inspect(token.getTokenValue());
        if (!decision.active()) {
            throw new InvalidBearerTokenException("Merchant machine token is inactive");
        }
        if (!owner.equals(ownerId(decision.merchantOwner()))) {
            return Optional.empty();
        }
        return Optional.of(new BookingIdentity(owner, clientId));
    }

    private static String stringClaim(Jwt token, String name) {
        Object value = token.getClaims().get(name);
        return value instanceof String text ? text : "";
    }

    private static UUID ownerId(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
