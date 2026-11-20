package com.swiftdrop.backend.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code swiftdrop.identity.*}: how SwiftDrop's backend logs in to the auth service's Directory API.
 * The client id and secret come from application-local.properties (git-ignored) or the server's secrets, never git.
 *
 * @param clientId        our service client, e.g. svc_swiftdrop-backend (empty = directory off: only the local copy)
 * @param clientSecret    its secret
 * @param refreshInterval how often the copy is refreshed from auth (default 10 minutes)
 */
@ConfigurationProperties("swiftdrop.identity")
public record IdentityProperties(String clientId, String clientSecret, Duration refreshInterval) {

    private static final Duration DEFAULT_REFRESH = Duration.ofMinutes(10);

    public IdentityProperties {
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret;
        if (!clientId.isEmpty() && !clientId.startsWith("svc_")) {
            throw new IllegalArgumentException("swiftdrop.identity.client-id must be a service client (svc_...)");
        }
        if (!clientId.isEmpty() && clientSecret.isBlank()) {
            throw new IllegalArgumentException("swiftdrop.identity.client-secret is required with a client-id");
        }
        refreshInterval = refreshInterval == null ? DEFAULT_REFRESH : refreshInterval;
    }

    public boolean directoryEnabled() {
        return !clientId.isEmpty();
    }

    /** Never print the secret. */
    @Override
    public String toString() {
        return "IdentityProperties[clientId=" + clientId + ", refreshInterval=" + refreshInterval + "]";
    }
}
