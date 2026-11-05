package com.swiftdrop.backend.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Every request needs an access token from the SwiftDrop auth service, except GET /api/public/** (public tracking). The token is checked HERE, without calling
 * the auth service: RS256 signature against its public keys (JWKS, cached), type "at+jwt", issuer, audience, expiry.
 * The token's "roles" become ROLE_* authorities and its "entitlements" (permissions) become authorities as they are.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SwiftDropAuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        // Public parcel tracking: no login, read-only, answers hold no private data
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())))
                // Tokens, not cookies: no session and nothing for CSRF to abuse
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(SwiftDropAuthProperties auth) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(auth.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // Only access tokens (RFC 9068 "at+jwt"), never another kind of token signed by the same key
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<SecurityContext>(new JOSEObjectType("at+jwt"))))
                .build();
        decoder.setJwtValidator(tokenValidator(auth));
        return decoder;
    }

    // The access-token (RFC 9068) validator: typ "at+jwt", exact issuer, audience, expiry, required claims.
    // Not createDefaultWithIssuer(): in Spring Security 7 that one only accepts typ "JWT" and rejects our tokens.
    static OAuth2TokenValidator<Jwt> tokenValidator(SwiftDropAuthProperties auth) {
        return JwtValidators.createAtJwtValidator()
                .issuer(auth.issuer())
                .audience(auth.audience())
                .build();
    }

    static JwtAuthenticationConverter authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authorities);
        return converter;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList("roles");
        List<String> entitlements = jwt.getClaimAsStringList("entitlements");
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        if (entitlements != null) {
            entitlements.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return authorities;
    }

    // Only the listed frontends may call this API from a browser; tokens travel in the header, never as cookies
    @Bean
    CorsConfigurationSource corsConfigurationSource(SwiftDropAuthProperties auth) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(auth.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
