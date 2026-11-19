package com.swiftdrop.backend.pricing.maps;

import com.swiftdrop.backend.pricing.DistanceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Road distance from the Google Maps Routes API ({@code directions/v2:computeRoutes}): driving, without traffic, with
 * addresses resolved in Sri Lanka. The key is {@code swiftdrop.maps.google.api-key} (env {@code GOOGLE_MAPS_API_KEY});
 * without one, no call is made and quotes use the district bases.
 * <p>
 * Answers are kept for a day (up to 1,000 address pairs): a customer's estimate and the booking that follows it ask
 * for the same route, so Google is billed once and both get the same distance, so the same fee.
 */
@Component
class GoogleRoutesDistanceSource implements DistanceSource {

    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesDistanceSource.class);
    static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final Duration CACHE_FOR = Duration.ofHours(24);
    private static final int CACHE_SIZE = 1_000;

    private final RestClient http;
    private final String apiKey;
    private final BigDecimal maxKm;
    private final Clock clock;
    private final Map<String, Cached> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private record Cached(RouteDistance distance, Instant until) { }

    @Autowired
    GoogleRoutesDistanceSource(@Value("${swiftdrop.maps.google.api-key:}") String apiKey,
                               @Value("${swiftdrop.maps.max-route-km:800}") BigDecimal maxKm,
                               Clock clock) {
        this(RestClient.builder().requestFactory(timeouts()).build(), apiKey, maxKm, clock);
    }

    GoogleRoutesDistanceSource(RestClient http, String apiKey, BigDecimal maxKm, Clock clock) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxKm = maxKm;
        this.clock = clock;
        if (this.apiKey.isEmpty()) {
            log.info("No Google Maps API key (swiftdrop.maps.google.api-key): quotes use the district rates");
        }
    }

    @Override
    public Optional<RouteDistance> drivingDistance(String origin, String destination) {
        if (apiKey.isEmpty() || origin == null || destination == null || origin.isBlank() || destination.isBlank()) {
            return Optional.empty();
        }
        String key = normalise(origin) + "\n" + normalise(destination);
        Instant now = clock.instant();
        synchronized (cache) {
            Cached hit = cache.get(key);
            if (hit != null && hit.until().isAfter(now)) {
                return Optional.of(hit.distance());
            }
        }
        Optional<RouteDistance> answer = ask(origin, destination);
        answer.ifPresent(distance -> {
            synchronized (cache) {
                cache.put(key, new Cached(distance, now.plus(CACHE_FOR)));
            }
        });
        return answer;
    }

    private Optional<RouteDistance> ask(String origin, String destination) {
        Map<String, Object> body = Map.of(
                "origin", Map.of("address", origin),
                "destination", Map.of("address", destination),
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_UNAWARE",
                "regionCode", "lk",
                "units", "METRIC");
        JsonNode answer;
        try {
            answer = http.post().uri(ROUTES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException failure) {
            // The message can't hold the key: it is sent as a header, never in the URL
            log.warn("Google Routes API failed, quoting with district rates: {}", failure.getMessage());
            return Optional.empty();
        }
        JsonNode route = answer == null ? null : answer.path("routes").path(0);
        if (route == null || !route.has("distanceMeters")) {
            log.info("Google found no driving route; quoting with district rates");
            return Optional.empty();
        }
        BigDecimal km = BigDecimal.valueOf(route.path("distanceMeters").asLong())
                .divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        if (km.compareTo(maxKm) > 0) {
            // Longer than any road in Sri Lanka: Google placed an address somewhere else
            log.warn("Google route of {} km is over the {} km limit; quoting with district rates", km, maxKm);
            return Optional.empty();
        }
        return Optional.of(new RouteDistance(km, parseSeconds(route.path("duration").asString(""))));
    }

    /** Google writes durations as seconds with an "s", e.g. "8412s". */
    private static Duration parseSeconds(String text) {
        try {
            return Duration.ofSeconds(Long.parseLong(text.endsWith("s") ? text.substring(0, text.length() - 1) : text));
        } catch (NumberFormatException notANumber) {
            return Duration.ZERO;
        }
    }

    private static String normalise(String address) {
        return address.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
