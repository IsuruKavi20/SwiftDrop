package com.swiftdrop.backend.identity.client;

import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.identity.entity.AppUserStatus;
import com.swiftdrop.backend.identity.exception.DirectoryUnavailableException;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Talks to the auth service: logs in with client credentials (POST /oauth2/token), keeps the 15-minute token
 * until shortly before it ends, and reads people from the Directory API. Any failure becomes
 * {@link DirectoryUnavailableException}, so callers have one thing to handle.
 */
public class DirectoryClient {

    public static final int MAX_LOOKUP = 100;
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(60);
    private static final String USERS = "/api/v1/directory/users";
    private static final String EVENTS_POLL = "/api/v1/events/poll";

    private final RestClient http;
    private final IdentityProperties properties;
    private final Clock clock;
    private String token;
    private Instant tokenExpiresAt = Instant.MIN;

    public DirectoryClient(RestClient http, IdentityProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    /** Empty when the auth service doesn't know this id (404). */
    public Optional<DirectoryPerson> get(UUID id, boolean withEmail) {
        try {
            JsonNode body = http.get().uri(USERS + "/{id}", id)
                    .headers(headers -> headers.setBearerAuth(token()))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> { })
                    .body(JsonNode.class);
            if (body == null || !body.hasNonNull("id")) {
                return Optional.empty();
            }
            return Optional.of(toPerson(body, withEmail));
        } catch (RestClientException failure) {
            throw new DirectoryUnavailableException("Directory lookup failed", failure);
        }
    }

    /** People for these ids (at most 100). Unknown ids are simply missing from the answer. */
    public List<DirectoryPerson> lookup(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_LOOKUP) {
            throw new IllegalArgumentException("At most " + MAX_LOOKUP + " ids per lookup");
        }
        try {
            JsonNode body = http.post().uri(USERS + "/lookup")
                    .headers(headers -> headers.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", List.copyOf(ids)))
                    .retrieve()
                    .body(JsonNode.class);
            List<DirectoryPerson> people = new ArrayList<>();
            if (body != null) {
                body.path("Resources").forEach(node -> people.add(toPerson(node, false)));
            }
            return List.copyOf(people);
        } catch (RestClientException failure) {
            throw new DirectoryUnavailableException("Directory lookup failed", failure);
        }
    }

    /**
     * RFC 8936 poll: acknowledges what we processed (and reports SETs we refused), then returns up to maxEvents new
     * SETs (jti → serialized SET). Needs the permission events:read on our service client.
     */
    public PollAnswer pollEvents(List<String> ack, Map<String, Map<String, String>> setErrs, int maxEvents) {
        try {
            JsonNode body = http.post().uri(EVENTS_POLL)
                    .headers(headers -> headers.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ack", List.copyOf(ack), "setErrs", Map.copyOf(setErrs), "maxEvents", maxEvents))
                    .retrieve()
                    .body(JsonNode.class);
            Map<String, String> sets = new java.util.LinkedHashMap<>();
            if (body != null) {
                body.path("sets").properties().forEach(entry -> sets.put(entry.getKey(), entry.getValue().asString()));
            }
            return new PollAnswer(sets, body != null && body.path("moreAvailable").asBoolean(false));
        } catch (RestClientException failure) {
            throw new DirectoryUnavailableException("Event poll failed", failure);
        }
    }

    /** @param sets jti → serialized SET, in the order auth sent them */
    public record PollAnswer(Map<String, String> sets, boolean moreAvailable) {
    }

    private synchronized String token() {
        if (!properties.directoryEnabled()) {
            throw new DirectoryUnavailableException("The directory isn't configured (swiftdrop.identity.client-id)");
        }
        Instant now = clock.instant();
        if (token != null && now.isBefore(tokenExpiresAt.minus(RENEW_BEFORE_EXPIRY))) {
            return token;
        }
        JsonNode answer = http.post().uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret(),
                        StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // Never log the answer body or our secret; the status is enough to diagnose
                    throw new DirectoryUnavailableException("Directory login refused: HTTP " + response.getStatusCode());
                })
                .body(JsonNode.class);
        if (answer == null || !answer.hasNonNull("access_token")) {
            throw new DirectoryUnavailableException("Directory login gave no token");
        }
        token = answer.get("access_token").asString();
        tokenExpiresAt = now.plusSeconds(answer.path("expires_in").asLong(0));
        return token;
    }

    private static DirectoryPerson toPerson(JsonNode node, boolean withEmail) {
        List<String> roles = new ArrayList<>();
        node.path("roles").forEach(role -> roles.add(role.asString()));
        JsonNode modified = node.path("meta").path("lastModified");
        return new DirectoryPerson(
                UUID.fromString(node.get("id").asString()),
                text(node, "name"),
                text(node, "picture"),
                AppUserStatus.valueOf(node.path("status").asString("DISABLED")),
                node.path("active").asBoolean(false),
                roles,
                modified.isString() ? Instant.parse(modified.asString()) : null,
                withEmail ? text(node, "email") : null);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }
}
