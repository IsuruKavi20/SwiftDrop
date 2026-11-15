package com.swiftdrop.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sends open outbox events again (e.g. after RabbitMQ was down). Every minute, events still open after 1 minute are
 * resubmitted to their listener. Events left over from a crash are also resubmitted at start-up
 * (spring.modulith.events.republish-outstanding-events-on-restart).
 * With more than one backend copy, only one should run this (a lock such as ShedLock): fine today, we run one.
 */
@Component
class OutboxResubmitter {

    private static final Logger log = LoggerFactory.getLogger(OutboxResubmitter.class);
    private static final Duration OLDER_THAN = Duration.ofMinutes(1);

    private final IncompleteEventPublications incomplete;

    OutboxResubmitter(IncompleteEventPublications incomplete) {
        this.incomplete = incomplete;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.outbox.resubmit-every:PT1M}", initialDelayString = "PT1M")
    void resubmit() {
        try {
            incomplete.resubmitIncompletePublicationsOlderThan(OLDER_THAN);
        } catch (RuntimeException failure) {
            log.warn("Outbox resubmit failed, will try again: {}", failure.getMessage());
        }
    }
}
