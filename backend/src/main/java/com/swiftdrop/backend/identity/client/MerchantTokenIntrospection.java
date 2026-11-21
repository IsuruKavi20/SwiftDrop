package com.swiftdrop.backend.identity.client;

import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.security.SwiftDropAuthProperties;
import com.swiftdrop.backend.shared.error.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@NamedInterface("merchant-introspection")
public class MerchantTokenIntrospection {
    private final RestClient http;
    private final IdentityProperties serviceClient;

    @Autowired
    public MerchantTokenIntrospection(SwiftDropAuthProperties auth, IdentityProperties serviceClient) {
        this(httpClient(auth), serviceClient);
    }

    MerchantTokenIntrospection(RestClient http, IdentityProperties serviceClient) {
        this.http = http;
        this.serviceClient = serviceClient;
    }

    public Result inspect(String accessToken) {
        if (!serviceClient.directoryEnabled()) {
            throw unavailable();
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", accessToken);
        JsonNode answer;
        try {
            answer = http.post().uri("/oauth2/introspect")
                    .headers(headers -> headers.setBasicAuth(serviceClient.clientId(), serviceClient.clientSecret(),
                            StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException failure) {
            // Upstream exception bodies can contain credentials or token details.
            throw unavailable();
        }

        if (answer == null || !answer.path("active").isBoolean()) {
            throw unavailable();
        }
        return new Result(answer.path("active").booleanValue(),
                answer.path("merchant_owner").isString() ? answer.path("merchant_owner").asString() : null);
    }

    private static RestClient httpClient(SwiftDropAuthProperties auth) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(auth.issuer()).requestFactory(timeouts).build();
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("Merchant token introspection is unavailable");
    }

    public record Result(boolean active, String merchantOwner) {}
}
