package com.swiftdrop.backend.identity.client;

import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.shared.error.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MerchantTokenIntrospectionTest {
    private static final String URL = "https://auth.test/oauth2/introspect";
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.test");
    private final MockRestServiceServer auth = MockRestServiceServer.bindTo(builder).build();
    private final MerchantTokenIntrospection client = new MerchantTokenIntrospection(builder.build(),
            new IdentityProperties("svc_swiftdrop-backend", "fixture-secret", null));

    @Test
    void postsOneFormTokenUsingBackendBasicCredentials() {
        String basic = Base64.getEncoder().encodeToString("svc_swiftdrop-backend:fixture-secret".getBytes(StandardCharsets.UTF_8));
        auth.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basic))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("token=fixture%2Btoken"))
                .andRespond(withSuccess("{\"active\":true,\"merchant_owner\":\"fixture-owner\"}", MediaType.APPLICATION_JSON));
        assertThat(client.inspect("fixture+token")).isEqualTo(new MerchantTokenIntrospection.Result(true, "fixture-owner"));
        auth.verify();
    }

    @Test
    void acceptsInactiveDecisionWithoutOwner() {
        auth.expect(requestTo(URL)).andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));
        assertThat(client.inspect("fixture")).isEqualTo(new MerchantTokenIntrospection.Result(false, null));
        auth.verify();
    }

    @Test
    void rejectsUnexpectedStatusAndMalformedDecision() {
        auth.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("upstream-private-body"));
        auth.expect(requestTo(URL)).andRespond(withSuccess("{\"active\":\"true\"}", MediaType.APPLICATION_JSON));
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> client.inspect("fixture")).isInstanceOf(ServiceUnavailableException.class)
                    .hasMessage("Merchant token introspection is unavailable").hasNoCause();
        }
        auth.verify();
    }
}
