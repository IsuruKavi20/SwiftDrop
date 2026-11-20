package com.swiftdrop.backend.identity.service;

import com.swiftdrop.backend.identity.client.DirectoryClient;
import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.identity.controller.IdentityEventsController;
import com.swiftdrop.backend.identity.exception.DirectoryUnavailableException;
import com.swiftdrop.backend.identity.repository.AppUserWriter;
import com.swiftdrop.backend.identity.security.InvalidSetException;
import com.swiftdrop.backend.identity.security.SetVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RFC 8936 catch-up: on start and every few minutes, collects identity events we missed (we were down, or auth can't
 * reach us yet) and applies them exactly like pushed ones. Each round acknowledges the previous round's events; a
 * refused SET is reported in setErrs. An event that failed for another reason (e.g. database down) is NOT
 * acknowledged, so auth sends it again next time; repeats are harmless.
 */
@Component
public class IdentityEventPoller {

    static final int MAX_EVENTS = 50;
    private static final int MAX_ROUNDS = 20;   // at most 1,000 events per run, so one run never runs forever
    // Auth stops retrying after 24 h; older repeats are still stopped by the per-person version check
    private static final Duration KEEP_PROCESSED_IDS = Duration.ofDays(7);
    private static final Logger log = LoggerFactory.getLogger(IdentityEventPoller.class);

    private final DirectoryClient client;
    private final SetVerifier verifier;
    private final IdentityEventApplier applier;
    private final IdentityProperties properties;
    private final AppUserWriter writer;
    private final Clock clock;

    IdentityEventPoller(DirectoryClient client, SetVerifier verifier, IdentityEventApplier applier,
                        IdentityProperties properties, AppUserWriter writer, Clock clock) {
        this.client = client;
        this.verifier = verifier;
        this.applier = applier;
        this.properties = properties;
        this.writer = writer;
        this.clock = clock;
    }

    /** Once an hour: forget processed event ids older than 7 days, so the table never grows forever. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    void forgetOldEventIds() {
        int forgotten = writer.forgetProcessedBefore(clock.instant().minus(KEEP_PROCESSED_IDS));
        if (forgotten > 0) {
            log.info("Old processed identity event ids removed count={}", forgotten);
        }
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${swiftdrop.identity.event-poll-interval:PT2M}")
    void scheduled() {
        if (!properties.directoryEnabled()) {
            return;
        }
        try {
            int applied = pollUntilEmpty();
            if (applied > 0) {
                log.info("Identity events collected by polling count={}", applied);
            }
        } catch (DirectoryUnavailableException unavailable) {
            // The copy still works (and the directory refresh still runs); next round tries again
            log.warn("Identity event poll skipped: {}", unavailable.getMessage());
        }
    }

    /** @return how many events were processed (applied, repeats and outdated ones) */
    synchronized int pollUntilEmpty() {
        List<String> ack = new ArrayList<>();
        Map<String, Map<String, String>> setErrs = new LinkedHashMap<>();
        int processed = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            DirectoryClient.PollAnswer answer = client.pollEvents(ack, setErrs, MAX_EVENTS);
            ack = new ArrayList<>();
            setErrs = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : answer.sets().entrySet()) {
                if (handle(entry.getKey(), entry.getValue(), setErrs)) {
                    ack.add(entry.getKey());
                    processed++;
                }
            }
            // No progress (everything failed for a passing reason, e.g. database down): stop, try next run
            boolean noProgress = !answer.sets().isEmpty() && ack.isEmpty() && setErrs.isEmpty();
            if (!answer.moreAvailable() || noProgress) {
                break;
            }
        }
        if (!ack.isEmpty() || !setErrs.isEmpty()) {
            try {
                client.pollEvents(ack, setErrs, 0);   // just acknowledge, ask for nothing new
            } catch (DirectoryUnavailableException ackLost) {
                // Not acknowledged: auth sends them again and they come back as harmless repeats
                log.warn("Identity event acknowledgement failed: {}", ackLost.getMessage());
            }
        }
        return processed;
    }

    // true = acknowledge it
    private boolean handle(String jti, String set, Map<String, Map<String, String>> setErrs) {
        try {
            applier.apply(verifier.verify(set));
            return true;
        } catch (DataIntegrityViolationException sameEventAtTheSameMoment) {
            return true;   // another delivery applied it at the same moment (push and poll, or two servers)
        } catch (InvalidSetException invalid) {
            log.warn("Polled identity event refused jti={}: {}", IdentityEventsController.safeForLog(jti),
                    IdentityEventsController.safeForLog(invalid.getMessage()));
            if (isEventId(jti)) {
                setErrs.put(jti, Map.of("err", "invalid_request", "description", "invalid SET"));
            }
            return false;
        } catch (RuntimeException notNow) {
            log.error("Polled identity event not applied (will come again) jti={}",
                    IdentityEventsController.safeForLog(jti), notNow);
            return false;
        }
    }

    // Auth's event ids are UUIDs; anything else must never be sent back (auth would refuse the whole request)
    private static boolean isEventId(String jti) {
        try {
            UUID.fromString(jti);
            return true;
        } catch (IllegalArgumentException notAnId) {
            return false;
        }
    }
}
