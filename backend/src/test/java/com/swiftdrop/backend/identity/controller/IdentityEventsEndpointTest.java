package com.swiftdrop.backend.identity.controller;

import com.swiftdrop.backend.identity.security.RateLimiter;
import com.swiftdrop.backend.identity.security.SetVerifier;
import com.swiftdrop.backend.identity.security.SetVerifierTest;
import com.swiftdrop.backend.identity.service.IdentityEventApplier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The push endpoint end to end, with SETs signed by a test key that plays the auth service. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class IdentityEventsEndpointTest {

    static final RSAKey AUTH_KEY = generate();

    /** The real SetVerifier, trusting the test key instead of downloading auth's JWKS. Same checks. */
    @TestConfiguration
    static class TestAuthKeys {
        @Bean
        @Primary
        SetVerifier testSetVerifier() throws Exception {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(AUTH_KEY.toRSAPublicKey())
                    .jwtProcessorCustomizer(SetVerifier.ONLY_SECURITY_EVENT_TOKENS).build();
            return new SetVerifier(decoder, "https://auth.getswiftdrop.me");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID kasun = UUID.randomUUID();

    @Test
    void validSetGets202AndIsApplied() throws Exception {
        push(set("e1", 1, Map.of("name", "Kasun Silva"))).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT name FROM app_users WHERE id = ?", String.class, kasun))
                .isEqualTo("Kasun Silva");
    }

    @Test
    void aRepeatAlsoGets202() throws Exception {
        String set = set("e1", 1, Map.of("name", "Kasun Silva"));
        push(set).andExpect(status().isAccepted());
        push(set).andExpect(status().isAccepted());
    }

    @Test
    void forgedSignatureGets400AndChangesNothing() throws Exception {
        RSAKey stranger = new RSAKeyGenerator(2048).keyID(AUTH_KEY.getKeyID()).generate();
        String forged = SetVerifierTest.sign(stranger, "secevent+jwt", claims("e1", 1, Map.of("name", "Evil")).build());

        push(forged).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.err").value("invalid_request"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id = ?", Integer.class, kasun)).isZero();
    }

    @Test
    void accessTokenSentGets400() throws Exception {
        String accessToken = SetVerifierTest.sign(AUTH_KEY, "at+jwt", claims("e1", 1, Map.of())
                .expirationTime(Date.from(Instant.now().plusSeconds(900))).build());

        push(accessToken).andExpect(status().isBadRequest());
    }

    @Test
    void aHugeBodyIsRefusedWithoutReadingIt() throws Exception {
        mockMvc.perform(post("/internal/identity-events").contentType(IdentityEventsController.SECEVENT_JWT)
                        .content("x".repeat(70_000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logLinesCannotBeForged() {
        assertThat(IdentityEventsController.safeForLog("typ\r\nFAKE LOG LINE")).doesNotContain("\n", "\r");
        assertThat(IdentityEventsController.safeForLog("x".repeat(500))).hasSize(200);
    }

    @Test
    void anotherContentTypeIsNotAccepted() throws Exception {
        mockMvc.perform(post("/internal/identity-events").contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void theRateLimiterRefusesAFlood() {
        Clock frozen = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(20, frozen);

        int allowed = 0;
        for (int i = 0; i < 25; i++) {
            allowed += limiter.tryAcquire() ? 1 : 0;
        }

        assertThat(allowed).isEqualTo(20);
    }

    private ResultActions push(String set) throws Exception {
        return mockMvc.perform(post("/internal/identity-events")
                .contentType(IdentityEventsController.SECEVENT_JWT).content(set));
    }

    private String set(String jti, long version, Map<String, Object> profile) throws Exception {
        return SetVerifierTest.sign(AUTH_KEY, "secevent+jwt", claims(jti, version, profile).build());
    }

    private com.nimbusds.jwt.JWTClaimsSet.Builder claims(String jti, long version, Map<String, Object> profile) {
        return SetVerifierTest.claims(kasun)
                .issuer("https://auth.getswiftdrop.me")
                .jwtID(kasun + "-" + jti)
                .claim("events", Map.of(IdentityEventApplier.PROFILE_UPDATED, profile))
                .claim("swiftdrop_version", version);
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID("auth-key").generate();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
