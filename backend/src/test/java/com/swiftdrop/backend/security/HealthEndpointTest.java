package com.swiftdrop.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kubernetes asks the private management port "are you alive / ready?" without a token.
 * The public API port does not serve health at all (so the internet can't probe it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class HealthEndpointTest {

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int apiPort;

    @Test
    void livenessAndReadinessAnswerWithoutLogin() {
        RestClient management = RestClient.create("http://localhost:" + managementPort);
        assertThat(management.get().uri("/actuator/health/liveness").retrieve().body(String.class)).contains("UP");
        assertThat(management.get().uri("/actuator/health/readiness").retrieve().body(String.class)).contains("UP");
    }

    @Test
    void thePublicPortDoesNotServeHealth() {
        RestClient api = RestClient.create("http://localhost:" + apiPort);
        assertThatThrownBy(() -> api.get().uri("/actuator/health").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class);
    }
}
