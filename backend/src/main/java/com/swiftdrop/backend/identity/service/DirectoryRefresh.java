package com.swiftdrop.backend.identity.service;

import com.swiftdrop.backend.identity.client.DirectoryClient;
import com.swiftdrop.backend.identity.client.DirectoryPerson;
import com.swiftdrop.backend.identity.config.IdentityProperties;
import com.swiftdrop.backend.identity.exception.DirectoryUnavailableException;
import com.swiftdrop.backend.identity.repository.AppUserWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the copy fresh: every refresh interval, re-reads (in groups of 100) the people copied longest ago.
 * Blocks, name changes and deletions in auth reach SwiftDrop within one interval.
 */
@Component
public class DirectoryRefresh {

    private static final int MAX_ROUNDS = 50;   // at most 5,000 people per run, so one run never runs forever
    private static final Logger log = LoggerFactory.getLogger(DirectoryRefresh.class);

    private final AppUserWriter writer;
    private final DirectoryClient directory;
    private final IdentityProperties properties;
    private final Clock clock;

    DirectoryRefresh(AppUserWriter writer, DirectoryClient directory, IdentityProperties properties, Clock clock) {
        this.writer = writer;
        this.directory = directory;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${swiftdrop.identity.refresh-interval:PT10M}")
    void scheduled() {
        if (!properties.directoryEnabled()) {
            return;
        }
        try {
            int refreshed = refreshStale();
            if (refreshed > 0) {
                log.info("Identity copy refreshed people={}", refreshed);
            }
        } catch (DirectoryUnavailableException unavailable) {
            // The copy still works; we'll try again next round
            log.warn("Identity refresh skipped: {}", unavailable.getMessage());
        }
    }

    /** @return how many people were checked */
    public int refreshStale() {
        int checked = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<UUID> stale = writer.staleIds(clock.instant().minus(properties.refreshInterval()),
                    DirectoryClient.MAX_LOOKUP);
            if (stale.isEmpty()) {
                break;
            }
            Instant lookedUpAt = clock.instant();
            List<DirectoryPerson> people = directory.lookup(stale);
            people.forEach(person -> writer.saveFromDirectory(person, lookedUpAt));
            Set<UUID> missing = new HashSet<>(stale);
            people.forEach(person -> missing.remove(person.id()));
            writer.markChecked(List.copyOf(missing));
            checked += stale.size();
        }
        return checked;
    }
}
