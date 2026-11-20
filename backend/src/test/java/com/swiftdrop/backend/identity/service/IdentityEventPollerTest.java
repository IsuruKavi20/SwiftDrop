package com.swiftdrop.backend.identity.service;

import com.swiftdrop.backend.identity.client.DirectoryClient;
import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.identity.repository.AppUserWriter;
import com.swiftdrop.backend.identity.security.SetVerifier;
import com.swiftdrop.backend.identity.security.SetVerifierTest;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Polling against a fake auth server: real signed SETs, the real applier and database. */
@SpringBootTest
@Transactional
public class IdentityEventPollerTest {

    private static final String AUTH = "https://auth.getswiftdrop.me";
    private static final String TOKEN = "{\"access_token\":\"svc-token\",\"expires_in\":900}";

    @Autowired
    private IdentityEventApplier applier;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserWriter writer;

    private RSAKey authKey;
    private MockRestServiceServer auth;
    private IdentityEventPoller poller;

    @BeforeEach
    void fakeAuth() throws Exception {
        authKey = new RSAKeyGenerator(2048).keyID("auth-key").generate();
        RestClient.Builder builder = RestClient.builder().baseUrl(AUTH);
        auth = MockRestServiceServer.bindTo(builder).build();
        IdentityProperties properties = new IdentityProperties("svc_swiftdrop-backend", "s3cret", null);
        DirectoryClient client = new DirectoryClient(builder.build(), properties, Clock.systemUTC());
        SetVerifier verifier = new SetVerifier(NimbusJwtDecoder.withPublicKey(authKey.toRSAPublicKey())
                .jwtProcessorCustomizer(SetVerifier.ONLY_SECURITY_EVENT_TOKENS).build(), AUTH);
        poller = new IdentityEventPoller(client, verifier, applier, properties, writer, Clock.systemUTC());
    }

    @Test
    void pollingAppliesAndAcks() throws Exception {
        UUID kasun = UUID.randomUUID();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        auth.expect(requestTo(AUTH + "/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        // Round 1: nothing to acknowledge yet; auth has two events and more waiting
        auth.expect(requestTo(AUTH + "/api/v1/events/poll")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer svc-token"))
                .andExpect(jsonPath("$.ack").isEmpty())
                .andRespond(withSuccess(pollAnswer(true, first, set(first, kasun, 1, "Kasun Silva"),
                        second, set(second, kasun, 2, "Kasun S.")), MediaType.APPLICATION_JSON));
        // Round 2: acknowledges both; nothing more
        auth.expect(requestTo(AUTH + "/api/v1/events/poll"))
                .andExpect(jsonPath("$.ack.length()").value(2))
                .andExpect(jsonPath("$.ack[0]").value(first))
                .andRespond(withSuccess("{\"sets\":{},\"moreAvailable\":false}", MediaType.APPLICATION_JSON));

        int processed = poller.pollUntilEmpty();

        assertThat(processed).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT name FROM app_users WHERE id = ?", String.class, kasun))
                .isEqualTo("Kasun S.");
        auth.verify();
    }

    @Test
    void aForgedPolledEventIsReportedNotApplied() throws Exception {
        UUID kasun = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        RSAKey stranger = new RSAKeyGenerator(2048).keyID("auth-key").generate();
        String forged = SetVerifierTest.sign(stranger, "secevent+jwt", claims(jti, kasun, 1, "Evil").build());
        auth.expect(requestTo(AUTH + "/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        auth.expect(requestTo(AUTH + "/api/v1/events/poll"))
                .andRespond(withSuccess(pollAnswer(false, jti, forged), MediaType.APPLICATION_JSON));
        // The final round reports the refused SET and acknowledges nothing
        auth.expect(requestTo(AUTH + "/api/v1/events/poll"))
                .andExpect(jsonPath("$.ack").isEmpty())
                .andExpect(jsonPath("$.setErrs['" + jti + "'].err").value("invalid_request"))
                .andExpect(jsonPath("$.maxEvents").value(0))
                .andRespond(withSuccess("{\"sets\":{},\"moreAvailable\":false}", MediaType.APPLICATION_JSON));

        assertThat(poller.pollUntilEmpty()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id = ?", Integer.class, kasun)).isZero();
        auth.verify();
    }

    @Test
    void processedIdsOlderThanSevenDaysAreForgotten() {
        jdbc.update("INSERT INTO processed_identity_events (jti, received_at) VALUES ('old', ?)",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(8))));
        jdbc.update("INSERT INTO processed_identity_events (jti, received_at) VALUES ('new', ?)",
                java.sql.Timestamp.from(java.time.Instant.now()));

        poller.forgetOldEventIds();

        assertThat(jdbc.queryForList("SELECT jti FROM processed_identity_events WHERE jti IN ('old', 'new')",
                String.class)).containsExactly("new");
    }

    private String set(String jti, UUID user, long version, String name) throws Exception {
        return SetVerifierTest.sign(authKey, "secevent+jwt", claims(jti, user, version, name).build());
    }

    private static com.nimbusds.jwt.JWTClaimsSet.Builder claims(String jti, UUID user, long version, String name) {
        return SetVerifierTest.claims(user).issuer(AUTH).jwtID(jti)
                .claim("events", Map.of(IdentityEventApplier.PROFILE_UPDATED, Map.of("name", name)))
                .claim("swiftdrop_version", version);
    }

    private static String pollAnswer(boolean more, String... jtiAndSet) {
        StringBuilder sets = new StringBuilder();
        for (int i = 0; i < jtiAndSet.length; i += 2) {
            sets.append(i == 0 ? "" : ",").append('"').append(jtiAndSet[i]).append("\":\"").append(jtiAndSet[i + 1])
                    .append('"');
        }
        return "{\"sets\":{" + sets + "},\"moreAvailable\":" + more + "}";
    }
}
