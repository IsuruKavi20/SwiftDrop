package com.swiftdrop.backend.identity.config;

import com.swiftdrop.backend.identity.client.DirectoryClient;
import com.swiftdrop.backend.identity.security.RateLimiter;
import com.swiftdrop.backend.identity.security.SetVerifier;
import com.swiftdrop.backend.security.SwiftDropAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfig {

    // A slow auth service must never hang our requests
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final int EVENTS_PER_SECOND = 20;

    @Bean
    DirectoryClient directoryClient(SwiftDropAuthProperties auth, IdentityProperties identity, Clock clock) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(CONNECT_TIMEOUT);
        timeouts.setReadTimeout(READ_TIMEOUT);
        RestClient http = RestClient.builder().baseUrl(auth.issuer()).requestFactory(timeouts).build();
        return new DirectoryClient(http, identity, clock);
    }

    /** Identity events: same auth keys (JWKS) as access tokens, but only typ secevent+jwt passes. */
    @Bean
    SetVerifier setVerifier(SwiftDropAuthProperties auth) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(CONNECT_TIMEOUT);
        timeouts.setReadTimeout(READ_TIMEOUT);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(auth.jwkSetUri())
                .restOperations(new org.springframework.web.client.RestTemplate(timeouts))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .jwtProcessorCustomizer(SetVerifier.ONLY_SECURITY_EVENT_TOKENS)
                .build();
        return new SetVerifier(decoder, auth.issuer());
    }

    @Bean
    RateLimiter identityEventsRateLimiter(Clock clock) {
        return new RateLimiter(EVENTS_PER_SECOND, clock);
    }
}
