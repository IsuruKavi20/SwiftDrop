package com.swiftdrop.backend.identity.controller;

import com.swiftdrop.backend.identity.security.InvalidSetException;
import com.swiftdrop.backend.identity.security.RateLimiter;
import com.swiftdrop.backend.identity.security.SetVerifier;
import com.swiftdrop.backend.identity.security.VerifiedSet;
import com.swiftdrop.backend.identity.service.IdentityEventApplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * RFC 8935 push delivery: the auth service POSTs each identity event (a signed SET) here.
 * 202 = taken (applied, a repeat, or older than what we have). 400 = the SET itself is invalid, which tells auth never
 * to resend it, so 400 is used for nothing else. Anything else (429, 500) makes auth retry later.
 */
@RestController
public class IdentityEventsController {

    public static final String SECEVENT_JWT = "application/secevent+jwt";
    // A real SET is about 1 KB; anything this big is not one
    private static final int MAX_SET_LENGTH = 64 * 1024;
    private static final Logger log = LoggerFactory.getLogger(IdentityEventsController.class);

    private final SetVerifier verifier;
    private final IdentityEventApplier applier;
    private final RateLimiter rateLimiter;

    IdentityEventsController(SetVerifier verifier, IdentityEventApplier applier, RateLimiter identityEventsRateLimiter) {
        this.verifier = verifier;
        this.applier = applier;
        this.rateLimiter = identityEventsRateLimiter;
    }

    // No login on purpose: only a SET signed by the auth service's key is accepted (see SetVerifier)
    @PostMapping(value = "/internal/identity-events", consumes = SECEVENT_JWT)
    @PreAuthorize("permitAll()")
    ResponseEntity<?> receive(HttpServletRequest request) throws IOException {
        // Cheap checks first, before reading the body or using a rate-limit permit
        if (request.getContentLengthLong() > MAX_SET_LENGTH) {
            return invalid("SET too large");
        }
        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1").build();
        }
        String set = readAtMost(request.getInputStream());
        if (set == null) {
            return invalid("SET too large");   // chunked body without a length
        }
        VerifiedSet verified;
        try {
            verified = verifier.verify(set.trim());
        } catch (InvalidSetException invalid) {
            log.warn("Identity event refused: {}", safeForLog(invalid.getMessage()));
            return invalid("invalid SET");
        }
        try {
            applier.apply(verified);
        } catch (DataIntegrityViolationException sameEventAtTheSameMoment) {
            // Another delivery of this event (or about this new person) won the race: it is applied
            log.info("Identity event raced with another delivery jti={} (treated as a repeat)", verified.jti());
        }
        return ResponseEntity.accepted().build();
    }

    // Reads up to the limit, never more: null if the body is bigger
    private static String readAtMost(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_SET_LENGTH + 1);
        return bytes.length > MAX_SET_LENGTH ? null : new String(bytes, StandardCharsets.US_ASCII);
    }

    // The reason can contain text from the request (e.g. a fake typ header): no line breaks, short
    public static String safeForLog(String text) {
        String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", "_");
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }

    // RFC 8935 section 2.3 error shape; the description stays generic (details only in our log)
    private static ResponseEntity<Map<String, String>> invalid(String description) {
        return ResponseEntity.badRequest().body(Map.of("err", "invalid_request", "description", description));
    }
}
