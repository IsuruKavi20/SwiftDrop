package com.swiftdrop.backend.identity.client;

import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.identity.exception.DirectoryUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class DirectoryClientTest {

    private static final String AUTH = "https://auth.test";
    private static final UUID KASUN = UUID.fromString("7f3a0000-0000-0000-0000-000000000001");
    private static final String TOKEN_ANSWER = "{\"access_token\":\"svc-token\",\"token_type\":\"Bearer\",\"expires_in\":900}";
    private static final String KASUN_JSON = "{\"id\":\"" + KASUN + "\",\"active\":true,\"status\":\"ACTIVE\","
            + "\"name\":\"Kasun Silva\",\"roles\":[\"COURIER\"],\"email\":\"kasun@example.com\","
            + "\"meta\":{\"lastModified\":\"2026-11-01T10:00:00Z\"}}";

    private MockRestServiceServer auth;
    private DirectoryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(AUTH);
        auth = MockRestServiceServer.bindTo(builder).build();
        IdentityProperties properties = new IdentityProperties("svc_swiftdrop-backend", "s3cret", null);
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);
        client = new DirectoryClient(builder.build(), properties, clock);
    }

    @Test
    void logsInOnceAndReadsAPerson() {
        auth.expect(once(), requestTo(AUTH + "/oauth2/token")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic c3ZjX3N3aWZ0ZHJvcC1iYWNrZW5kOnMzY3JldA=="))
                .andRespond(withSuccess(TOKEN_ANSWER, MediaType.APPLICATION_JSON));
        auth.expect(requestTo(AUTH + "/api/v1/directory/users/" + KASUN))
                .andExpect(header("Authorization", "Bearer svc-token"))
                .andRespond(withSuccess(KASUN_JSON, MediaType.APPLICATION_JSON));
        auth.expect(requestTo(AUTH + "/api/v1/directory/users/" + KASUN))
                .andExpect(header("Authorization", "Bearer svc-token"))
                .andRespond(withSuccess(KASUN_JSON, MediaType.APPLICATION_JSON));

        DirectoryPerson first = client.get(KASUN, false).orElseThrow();
        client.get(KASUN, false);   // the second call reuses the token: no second login

        assertThat(first.name()).isEqualTo("Kasun Silva");
        assertThat(first.roles()).containsExactly("COURIER");
        assertThat(first.active()).isTrue();
        assertThat(first.lastModified()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(first.email()).as("email only when asked for").isNull();
        auth.verify();
    }

    @Test
    void anUnknownPersonIsEmpty() {
        auth.expect(requestTo(AUTH + "/oauth2/token")).andRespond(withSuccess(TOKEN_ANSWER, MediaType.APPLICATION_JSON));
        auth.expect(requestTo(AUTH + "/api/v1/directory/users/" + KASUN)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.get(KASUN, false)).isEmpty();
    }

    @Test
    void looksUpManyAtOnce() {
        auth.expect(requestTo(AUTH + "/oauth2/token")).andRespond(withSuccess(TOKEN_ANSWER, MediaType.APPLICATION_JSON));
        auth.expect(requestTo(AUTH + "/api/v1/directory/users/lookup")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"totalResults\":1,\"Resources\":[" + KASUN_JSON + "]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup(List.of(KASUN))).extracting(DirectoryPerson::id).containsExactly(KASUN);
    }

    @Test
    void aRefusedLoginIsDirectoryUnavailable() {
        auth.expect(requestTo(AUTH + "/oauth2/token")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.get(KASUN, false))
                .isInstanceOf(DirectoryUnavailableException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void withoutAClientIdTheDirectoryIsOff() {
        DirectoryClient off = new DirectoryClient(RestClient.builder().baseUrl(AUTH).build(),
                new IdentityProperties(null, null, null), Clock.systemUTC());

        assertThatThrownBy(() -> off.get(KASUN, false)).isInstanceOf(DirectoryUnavailableException.class);
    }
}
