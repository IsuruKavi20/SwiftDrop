package com.swiftdrop.backend.pricing.maps;

import com.swiftdrop.backend.pricing.DistanceSource.RouteDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesDistanceSourceTest {

    private static final String COLOMBO = "1 Main St, Colombo, Colombo District, 00100, Sri Lanka";
    private static final String KANDY = "5 Temple Rd, Kandy, Kandy District, Sri Lanka";

    private RestClient.Builder builder;
    private MockRestServiceServer google;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T08:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        google = MockRestServiceServer.bindTo(builder).build();
    }

    private GoogleRoutesDistanceSource source(String key) {
        return new GoogleRoutesDistanceSource(builder.build(), key, new BigDecimal("800"), clock);
    }

    @Test
    void asksTheRoutesApiForADrivingRouteInSriLanka() {
        google.expect(once(), requestTo(GoogleRoutesDistanceSource.ROUTES_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration"))
                .andExpect(jsonPath("$.origin.address").value(COLOMBO))
                .andExpect(jsonPath("$.destination.address").value(KANDY))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.regionCode").value("lk"))
                .andRespond(withSuccess("{\"routes\":[{\"distanceMeters\":115437,\"duration\":\"10812s\"}]}",
                        MediaType.APPLICATION_JSON));

        Optional<RouteDistance> route = source("test-key").drivingDistance(COLOMBO, KANDY);

        assertThat(route).isPresent();
        assertThat(route.get().km()).isEqualByComparingTo("115.4");
        assertThat(route.get().duration()).isEqualTo(Duration.ofSeconds(10812));
        google.verify();
    }

    @Test
    void theSameRouteIsAskedForOnlyOnce() {
        google.expect(once(), requestTo(GoogleRoutesDistanceSource.ROUTES_URL))
                .andRespond(withSuccess("{\"routes\":[{\"distanceMeters\":115437,\"duration\":\"10812s\"}]}",
                        MediaType.APPLICATION_JSON));
        GoogleRoutesDistanceSource source = source("test-key");

        source.drivingDistance(COLOMBO, KANDY);
        // The booking after the estimate: same addresses, different spacing and case
        Optional<RouteDistance> again = source.drivingDistance("  1 main st,  Colombo, Colombo District, 00100, Sri Lanka", KANDY);

        assertThat(again).map(RouteDistance::km).hasValueSatisfying(km -> assertThat(km).isEqualByComparingTo("115.4"));
        google.verify();
    }

    @Test
    void withoutAKeyGoogleIsNotAsked() {
        assertThat(source("").drivingDistance(COLOMBO, KANDY)).isEmpty();
        assertThat(source(null).drivingDistance(COLOMBO, KANDY)).isEmpty();
        google.verify();
    }

    @Test
    void noRouteFound() {
        google.expect(requestTo(GoogleRoutesDistanceSource.ROUTES_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(source("test-key").drivingDistance(COLOMBO, KANDY)).isEmpty();
    }

    @Test
    void googleRefusingOrFailingGivesNoDistance() {
        google.expect(requestTo(GoogleRoutesDistanceSource.ROUTES_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(source("bad-key").drivingDistance(COLOMBO, KANDY)).isEmpty();
    }

    @Test
    void aRouteLongerThanSriLankaMeansAWrongAddress() {
        google.expect(requestTo(GoogleRoutesDistanceSource.ROUTES_URL))
                .andRespond(withSuccess("{\"routes\":[{\"distanceMeters\":2400000,\"duration\":\"90000s\"}]}",
                        MediaType.APPLICATION_JSON));
        assertThat(source("test-key").drivingDistance(COLOMBO, KANDY)).isEmpty();
    }
}
