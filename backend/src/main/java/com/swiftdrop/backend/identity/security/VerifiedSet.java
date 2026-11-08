package com.swiftdrop.backend.identity.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Security Event Token whose signature, type, issuer and audience were checked. Only {@link SetVerifier} makes these.
 *
 * @param jti       the event's id (repeats are ignored)
 * @param userId    who it is about ({@code sub_id.id}, the same id as the "sub" of their access tokens)
 * @param eventType the one event type URI
 * @param data      what changed (may be empty)
 * @param version   the person's event version; only a higher one than ours is applied
 */
public record VerifiedSet(String jti, UUID userId, String eventType, Map<String, Object> data, long version) {

    public VerifiedSet {
        // HashMap, not Map.copyOf: a null value is meaningful ("picture": null = photo removed)
        data = data == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(data));
    }
}
