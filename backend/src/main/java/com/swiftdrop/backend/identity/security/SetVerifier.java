package com.swiftdrop.backend.identity.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Checks identity events (RFC 8417 Security Event Tokens) from the auth service. Its own decoder, separate from the
 * login one, so a SET can never log anyone in and an access token can never pass as an event.
 * Order: signature against the auth JWKS first (Nimbus does this before any claim is used), then typ
 * {@code secevent+jwt}, exact issuer, our audience, and the required parts. A SET has no "exp": it states a fact.
 */
public class SetVerifier {

    public static final String AUDIENCE = "swiftdrop-backend";
    public static final String SET_TYPE = "secevent+jwt";
    // Same limits as the app_users columns: a value that can't be stored is an invalid SET, not a crash
    private static final int MAX_NAME = 200;
    private static final int MAX_PICTURE = 2048;
    private static final int MAX_ROLE = 40;
    private static final int MAX_ROLES = 50;
    private static final int MAX_STATUS = 20;

    /** Only typ "secevent+jwt" is accepted (an access token is "at+jwt", an ID token "JWT"). */
    public static final Consumer<ConfigurableJWTProcessor<SecurityContext>> ONLY_SECURITY_EVENT_TOKENS = processor ->
            processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(SET_TYPE)));

    private final NimbusJwtDecoder decoder;

    /** @param decoder RS256 with the auth keys, built with {@link #ONLY_SECURITY_EVENT_TOKENS} */
    public SetVerifier(NimbusJwtDecoder decoder, String issuer) {
        this.decoder = decoder;
        // Replaces Spring's default validators on purpose: no expiry check (SETs have none)
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuer), SetVerifier::audienceAndParts));
    }

    public VerifiedSet verify(String serializedSet) {
        Jwt set;
        try {
            set = decoder.decode(serializedSet);
        } catch (BadJwtException invalid) {
            throw new InvalidSetException("SET rejected: " + invalid.getMessage(), invalid);
        } catch (JwtException cannotCheckNow) {
            // e.g. auth's public keys can't be downloaded: NOT the SET's fault, so never a 400 (auth would drop it)
            throw new IllegalStateException("SET can't be checked right now", cannotCheckNow);
        }
        Map<String, Object> events = set.getClaimAsMap("events");
        Map.Entry<String, Object> event = events.entrySet().iterator().next();
        return new VerifiedSet(set.getId(), subject(set), event.getKey(), asMap(event.getValue()), version(set));
    }

    // Runs only after the signature was verified
    private static OAuth2TokenValidatorResult audienceAndParts(Jwt set) {
        if (set.getAudience() == null || !set.getAudience().contains(AUDIENCE)) {
            return failure("aud must contain " + AUDIENCE);
        }
        Object events = set.getClaims().get("events");
        if (!(events instanceof Map<?, ?> map) || map.size() != 1) {
            return failure("events must have exactly one entry");
        }
        if (set.getId() == null || set.getId().isBlank()) {
            return failure("jti is required");
        }
        try {
            subject(set);
        } catch (IllegalArgumentException | NullPointerException | ClassCastException missing) {
            return failure("sub_id.id must be a user id");
        }
        if (!(set.getClaims().get("swiftdrop_version") instanceof Number version) || version.longValue() < 1) {
            return failure("swiftdrop_version must be a number from 1");
        }
        Object data = map.values().iterator().next();
        if (data != null && !(data instanceof Map<?, ?>)) {
            return failure("event data must be an object");
        }
        String problem = data == null ? null : dataProblem((Map<?, ?>) data);
        return problem == null ? OAuth2TokenValidatorResult.success() : failure(problem);
    }

    // Only the fields we use are checked; anything else in the event is ignored
    private static String dataProblem(Map<?, ?> data) {
        if (!isText(data.get("name"), MAX_NAME)) {
            return "name must be text up to " + MAX_NAME;
        }
        if (!isText(data.get("picture"), MAX_PICTURE)) {
            return "picture must be text up to " + MAX_PICTURE;
        }
        if (!isText(data.get("status"), MAX_STATUS)) {
            return "status must be text";
        }
        Object roles = data.get("roles");
        if (roles != null && (!(roles instanceof Collection<?> list) || list.size() > MAX_ROLES
                || !list.stream().allMatch(role -> role != null && isText(role, MAX_ROLE)))) {
            return "roles must be a list of role names";
        }
        return null;
    }

    private static boolean isText(Object value, int maxLength) {
        return value == null || value instanceof String text && text.length() <= maxLength;
    }

    private static UUID subject(Jwt set) {
        Map<String, Object> subId = set.getClaimAsMap("sub_id");
        return UUID.fromString((String) subId.get("id"));
    }

    private static long version(Jwt set) {
        Object version = set.getClaims().get("swiftdrop_version");
        return ((Number) version).longValue();   // checked in audienceAndParts
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_request", description, null));
    }
}
