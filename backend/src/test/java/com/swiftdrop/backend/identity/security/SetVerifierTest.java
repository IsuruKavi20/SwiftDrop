package com.swiftdrop.backend.identity.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every SET is checked with real signatures: a test key plays the auth service's key. Nothing is skipped. */
public class SetVerifierTest {

    public static final String ISSUER = "https://auth.test";
    public static final String PROFILE_UPDATED = "https://schemas.swiftdrop.me/secevent/identity/profile-updated";

    private static RSAKey authKey;
    private static RSAKey strangerKey;
    private static SetVerifier verifier;
    private static final java.util.function.Consumer<com.nimbusds.jwt.proc.ConfigurableJWTProcessor<com.nimbusds.jose.proc.SecurityContext>> ONLY =
            SetVerifier.ONLY_SECURITY_EVENT_TOKENS;

    @BeforeAll
    static void keys() throws Exception {
        authKey = new RSAKeyGenerator(2048).keyID("auth-key").generate();
        strangerKey = new RSAKeyGenerator(2048).keyID("auth-key").generate();   // same kid, different key
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(authKey.toRSAPublicKey())
                .jwtProcessorCustomizer(SetVerifier.ONLY_SECURITY_EVENT_TOKENS).build();
        verifier = new SetVerifier(decoder, ISSUER);
    }

    @Test
    void aValidSetIsReadCompletely() throws Exception {
        UUID user = UUID.randomUUID();

        VerifiedSet set = verifier.verify(sign(authKey, "secevent+jwt", claims(user).build()));

        assertThat(set.jti()).isEqualTo("event-1");
        assertThat(set.userId()).isEqualTo(user);
        assertThat(set.eventType()).isEqualTo(PROFILE_UPDATED);
        assertThat(set.data()).containsEntry("name", "Kasun Silva");
        assertThat(set.version()).isEqualTo(7);
    }

    @Test
    void forgedSignatureIsRefused() throws Exception {
        String forged = sign(strangerKey, "secevent+jwt", claims(UUID.randomUUID()).build());

        assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(InvalidSetException.class);
    }

    @Test
    void accessTokenSentAsEventIsRefused() throws Exception {
        String accessToken = sign(authKey, "at+jwt", claims(UUID.randomUUID())
                .expirationTime(Date.from(Instant.now().plusSeconds(900))).build());

        assertThatThrownBy(() -> verifier.verify(accessToken)).isInstanceOf(InvalidSetException.class);
    }

    @Test
    void wrongTypIsRefused() throws Exception {
        String plainJwt = sign(authKey, "JWT", claims(UUID.randomUUID()).build());

        assertThatThrownBy(() -> verifier.verify(plainJwt)).isInstanceOf(InvalidSetException.class);
    }

    @Test
    void wrongAudienceIsRefused() throws Exception {
        String forSomeoneElse = sign(authKey, "secevent+jwt", claims(UUID.randomUUID()).audience("other-app").build());

        assertThatThrownBy(() -> verifier.verify(forSomeoneElse))
                .isInstanceOf(InvalidSetException.class).hasMessageContaining("aud");
    }

    @Test
    void wrongIssuerIsRefused() throws Exception {
        String fromElsewhere = sign(authKey, "secevent+jwt",
                claims(UUID.randomUUID()).issuer("https://auth.test.evil").build());

        assertThatThrownBy(() -> verifier.verify(fromElsewhere)).isInstanceOf(InvalidSetException.class);
    }

    @Test
    void exactlyOneEventIsRequired() throws Exception {
        String twoEvents = sign(authKey, "secevent+jwt", claims(UUID.randomUUID())
                .claim("events", Map.of(PROFILE_UPDATED, Map.of(), "https://x/other", Map.of())).build());

        assertThatThrownBy(() -> verifier.verify(twoEvents))
                .isInstanceOf(InvalidSetException.class).hasMessageContaining("events");
    }

    @Test
    void jtiAndSubjectAreRequired() throws Exception {
        String noJti = sign(authKey, "secevent+jwt", claims(UUID.randomUUID()).jwtID(null).build());
        String noSubject = sign(authKey, "secevent+jwt", claims(UUID.randomUUID()).claim("sub_id", null).build());

        assertThatThrownBy(() -> verifier.verify(noJti)).isInstanceOf(InvalidSetException.class);
        assertThatThrownBy(() -> verifier.verify(noSubject)).isInstanceOf(InvalidSetException.class);
    }

    @Test
    void aMissingVersionIsRefused() throws Exception {
        String noVersion = sign(authKey, "secevent+jwt", claims(UUID.randomUUID()).claim("swiftdrop_version", null).build());

        assertThatThrownBy(() -> verifier.verify(noVersion))
                .isInstanceOf(InvalidSetException.class).hasMessageContaining("swiftdrop_version");
    }

    @Test
    void badDataInsideASignedEventIsRefused() throws Exception {
        String nameNotText = sign(authKey, "secevent+jwt", claims(UUID.randomUUID())
                .claim("events", Map.of(PROFILE_UPDATED, Map.of("name", 42))).build());
        String nameTooLong = sign(authKey, "secevent+jwt", claims(UUID.randomUUID())
                .claim("events", Map.of(PROFILE_UPDATED, Map.of("name", "x".repeat(201)))).build());
        String rolesNotAList = sign(authKey, "secevent+jwt", claims(UUID.randomUUID())
                .claim("events", Map.of(PROFILE_UPDATED, Map.of("roles", "ADMIN"))).build());

        assertThatThrownBy(() -> verifier.verify(nameNotText)).isInstanceOf(InvalidSetException.class);
        assertThatThrownBy(() -> verifier.verify(nameTooLong)).isInstanceOf(InvalidSetException.class);
        assertThatThrownBy(() -> verifier.verify(rolesNotAList)).isInstanceOf(InvalidSetException.class);
    }

    @Test
    void aRemovedPhotoIsAllowed() throws Exception {
        java.util.Map<String, Object> removed = new java.util.HashMap<>();
        removed.put("picture", null);
        String photoRemoved = sign(authKey, "secevent+jwt", claims(UUID.randomUUID())
                .claim("events", Map.of(PROFILE_UPDATED, removed)).build());

        assertThat(verifier.verify(photoRemoved).data()).containsEntry("picture", null);
    }

    @Test
    void unreachableAuthKeysAreNotTheEventsFault() throws Exception {
        // Nothing listens on port 9: the keys can't be downloaded. That must NOT count as an invalid SET (400),
        // or auth would give a good event up for good.
        SetVerifier offline = new SetVerifier(NimbusJwtDecoder.withJwkSetUri("http://127.0.0.1:9/jwks.json")
                .jwtProcessorCustomizer(ONLY).build(), ISSUER);
        String valid = sign(authKey, "secevent+jwt", claims(UUID.randomUUID()).build());

        assertThatThrownBy(() -> offline.verify(valid)).isNotInstanceOf(InvalidSetException.class);
    }

    @Test
    void garbageIsRefused() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(InvalidSetException.class);
    }

    // ---- helpers (also used by the other identity-event tests) ----

    public static JWTClaimsSet.Builder claims(UUID user) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of(SetVerifier.AUDIENCE))
                .jwtID("event-1")
                .issueTime(new Date())
                .claim("sub_id", Map.of("format", "opaque", "id", user.toString()))
                .claim("events", Map.of(PROFILE_UPDATED, Map.of("name", "Kasun Silva")))
                .claim("swiftdrop_version", 7);
    }

    public static String sign(RSAKey key, String typ, JWTClaimsSet claims) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(typ))
                .keyID(key.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
