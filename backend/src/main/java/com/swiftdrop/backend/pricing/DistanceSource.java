package com.swiftdrop.backend.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * The road distance between two addresses, for distance-based quotes. The implementation asks the Google Maps
 * Routes API (driving, Sri Lanka). Empty when the distance can't be found: no API key configured, Google unreachable
 * or refusing, no route between the addresses, or a route too long to be inside Sri Lanka (a wrongly found address).
 * Callers then quote without distance; a quote never fails because of Google.
 */
public interface DistanceSource {

    Optional<RouteDistance> drivingDistance(String origin, String destination);

    /** A driving route: its length in km (1 decimal) and Google's travel time without traffic. */
    record RouteDistance(BigDecimal km, Duration duration) { }
}
