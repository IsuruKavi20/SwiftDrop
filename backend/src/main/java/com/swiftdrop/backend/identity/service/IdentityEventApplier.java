package com.swiftdrop.backend.identity.service;

import com.swiftdrop.backend.identity.entity.AppUserStatus;
import com.swiftdrop.backend.identity.event.RoleRevoked;
import com.swiftdrop.backend.identity.event.UserDeactivated;
import com.swiftdrop.backend.identity.event.UserDeleted;
import com.swiftdrop.backend.identity.repository.AppUserWriter;
import com.swiftdrop.backend.identity.security.VerifiedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a verified identity event to app_users. In ONE transaction: already seen (jti)? → lock the person and
 * compare versions → change the row → remember the jti. Repeats and older versions change nothing, so retries,
 * duplicates and events arriving in the wrong order are all safe. Other modules hear about it through
 * {@link UserDeleted}, {@link UserDeactivated} and {@link RoleRevoked}, published in the same transaction.
 */
@Component
public class IdentityEventApplier {

    public enum Outcome { APPLIED, REPEAT, OUTDATED }

    private static final String IDENTITY = "https://schemas.swiftdrop.me/secevent/identity/";
    private static final String RISC = "https://schemas.openid.net/secevent/risc/event-type/";
    private static final String CAEP = "https://schemas.openid.net/secevent/caep/event-type/";
    public static final String USER_REGISTERED = IDENTITY + "user-registered";
    public static final String PROFILE_UPDATED = IDENTITY + "profile-updated";
    public static final String ROLES_CHANGED = IDENTITY + "roles-changed";
    public static final String IDENTIFIER_CHANGED = RISC + "identifier-changed";
    public static final String ACCOUNT_DISABLED = RISC + "account-disabled";
    public static final String ACCOUNT_ENABLED = RISC + "account-enabled";
    public static final String ACCOUNT_PURGED = RISC + "account-purged";
    public static final String CREDENTIAL_CHANGE = CAEP + "credential-change";
    public static final String SESSION_REVOKED = CAEP + "session-revoked";

    private static final Logger log = LoggerFactory.getLogger(IdentityEventApplier.class);

    private final AppUserWriter writer;
    private final ApplicationEventPublisher moduleEvents;

    IdentityEventApplier(AppUserWriter writer, ApplicationEventPublisher moduleEvents) {
        this.writer = writer;
        this.moduleEvents = moduleEvents;
    }

    @Transactional
    public Outcome apply(VerifiedSet set) {
        if (writer.alreadyProcessed(set.jti())) {
            return Outcome.REPEAT;
        }
        Optional<Long> current = writer.lockEventVersion(set.userId());
        if (current.isPresent() && set.version() <= current.get()) {
            writer.rememberProcessed(set.jti());
            return Outcome.OUTDATED;
        }
        if (current.isEmpty()) {
            if (ACCOUNT_PURGED.equals(set.eventType())) {
                // Nothing of theirs here to erase
                writer.rememberProcessed(set.jti());
                return Outcome.APPLIED;
            }
            writer.insertPlaceholder(set.userId());   // the refresh fills in anything the event doesn't carry
        }
        change(set);
        writer.setEventVersion(set.userId(), set.version());
        writer.rememberProcessed(set.jti());
        // Ids only: never personal data in logs
        log.info("Identity event applied user={} type={} version={}", set.userId(), shortName(set.eventType()),
                set.version());
        return Outcome.APPLIED;
    }

    private void change(VerifiedSet set) {
        UUID id = set.userId();
        Map<String, Object> data = set.data();
        switch (set.eventType()) {
            case USER_REGISTERED -> {
                updateProfile(id, data);
                AppUserStatus status = status(data.get("status"));
                writer.setStatus(id, status, status == AppUserStatus.ACTIVE);
                if (data.containsKey("roles")) {
                    writer.replaceRoles(id, strings(data.get("roles")));
                }
            }
            case PROFILE_UPDATED -> updateProfile(id, data);
            case ROLES_CHANGED -> {
                if (!data.containsKey("roles")) {
                    log.warn("roles-changed without roles user={} (roles left as they are)", id);
                    return;
                }
                List<String> newRoles = strings(data.get("roles"));
                Set<String> lost = new HashSet<>(writer.roles(id));
                lost.removeAll(newRoles);
                writer.replaceRoles(id, newRoles);
                lost.forEach(role -> moduleEvents.publishEvent(new RoleRevoked(id, role)));
            }
            case ACCOUNT_DISABLED -> {
                writer.setStatus(id, AppUserStatus.DISABLED, false);
                moduleEvents.publishEvent(new UserDeactivated(id));
            }
            case ACCOUNT_ENABLED -> writer.setStatus(id, AppUserStatus.ACTIVE, true);
            case ACCOUNT_PURGED -> {
                writer.anonymise(id);
                moduleEvents.publishEvent(new UserDeleted(id));
            }
            // We don't store emails, passwords or sessions: the version moves on, nothing else changes
            case IDENTIFIER_CHANGED, CREDENTIAL_CHANGE, SESSION_REVOKED -> { }
            default -> log.warn("Unknown identity event type={} user={} (ignored)", set.eventType(), id);
        }
    }

    // Each SET carries only what changed: a field that is absent stays as it is
    private void updateProfile(UUID id, Map<String, Object> data) {
        if (data.containsKey("name")) {
            writer.setName(id, (String) data.get("name"));
        }
        if (data.containsKey("picture")) {
            writer.setPicture(id, (String) data.get("picture"));
        }
    }

    private static AppUserStatus status(Object value) {
        if (value == null || "ACTIVE".equals(value)) {
            return AppUserStatus.ACTIVE;
        }
        // Anything else (unknown, DELETED, PENDING_SYNC) must never make someone usable; deletion only via purge
        return "LOCKED".equals(value) ? AppUserStatus.LOCKED : AppUserStatus.DISABLED;
    }

    private static List<String> strings(Object value) {
        return value instanceof Collection<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String shortName(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }
}
