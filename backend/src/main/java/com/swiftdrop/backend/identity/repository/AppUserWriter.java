package com.swiftdrop.backend.identity.repository;

import com.swiftdrop.backend.identity.client.DirectoryPerson;
import com.swiftdrop.backend.identity.entity.AppUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only code that writes app_users (AppUser itself is @Immutable). Plain SQL that works the same on
 * PostgreSQL and the H2 test database: update the row, insert it if it wasn't there, then replace its roles.
 */
@Component
public class AppUserWriter {

    /** What a deleted person is shown as (old parcels still point to the row). */
    public static final String DELETED_NAME = "Deleted user";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    AppUserWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void save(DirectoryPerson person) {
        boolean deleted = person.status() == AppUserStatus.DELETED;
        String name = deleted ? DELETED_NAME : person.name();
        String picture = deleted ? null : person.picture();
        Timestamp modified = person.lastModified() == null ? null : Timestamp.from(person.lastModified());
        Timestamp now = now();

        int updated = jdbc.update("UPDATE app_users SET name = ?, picture_url = ?, status = ?, active = ?,"
                        + " last_modified = ?, synced_at = ? WHERE id = ?",
                name, picture, person.status().name(), person.active(), modified, now, person.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO app_users (id, name, picture_url, status, active, last_modified, synced_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    person.id(), name, picture, person.status().name(), person.active(), modified, now);
        }
        replaceRoles(person.id(), deleted ? List.of() : person.roles());
    }

    /**
     * Like {@link #save}, but for a copy read from the Directory API at {@code lookedUpAt}: skipped when an identity
     * event changed the row after that moment, so an older directory answer never overwrites a newer event.
     */
    @Transactional
    public void saveFromDirectory(DirectoryPerson person, Instant lookedUpAt) {
        List<Instant> synced = jdbc.queryForList("SELECT synced_at FROM app_users WHERE id = ? FOR UPDATE",
                Timestamp.class, person.id()).stream().map(time -> time == null ? null : time.toInstant()).toList();
        if (!synced.isEmpty() && synced.get(0) != null && synced.get(0).isAfter(lookedUpAt)) {
            return;
        }
        save(person);
    }

    /** Ids whose copy is older than the given time (or never filled in), oldest first. */
    public List<UUID> staleIds(Instant olderThan, int limit) {
        return jdbc.queryForList("SELECT id FROM app_users WHERE status <> 'DELETED'"
                        + " AND (synced_at IS NULL OR synced_at < ?) ORDER BY synced_at NULLS FIRST LIMIT ?",
                UUID.class, Timestamp.from(olderThan), limit);
    }

    /** Touch rows the directory didn't return (unknown to auth) so we don't ask about them every round. */
    public void markChecked(List<UUID> ids) {
        Timestamp now = now();
        ids.forEach(id -> jdbc.update("UPDATE app_users SET synced_at = ? WHERE id = ?", now, id));
    }

    // ---- identity events (always called inside the applier's one transaction) ----

    public boolean alreadyProcessed(String jti) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM processed_identity_events WHERE jti = ?",
                Integer.class, jti);
        return found != null && found > 0;
    }

    public void rememberProcessed(String jti) {
        jdbc.update("INSERT INTO processed_identity_events (jti, received_at) VALUES (?, ?)", jti, now());
    }

    /** @return how many remembered event ids were deleted */
    public int forgetProcessedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM processed_identity_events WHERE received_at < ?", Timestamp.from(cutoff));
    }

    /** Locks the person's row for this transaction and returns their last applied event version (empty = no row). */
    public Optional<Long> lockEventVersion(UUID id) {
        List<Long> versions = jdbc.queryForList("SELECT last_event_version FROM app_users WHERE id = ? FOR UPDATE",
                Long.class, id);
        return versions.stream().findFirst();
    }

    public void insertPlaceholder(UUID id) {
        jdbc.update("INSERT INTO app_users (id, status, active) VALUES (?, 'PENDING_SYNC', FALSE)", id);
    }

    public void setEventVersion(UUID id, long version) {
        jdbc.update("UPDATE app_users SET last_event_version = ?, synced_at = ? WHERE id = ?", version, now(), id);
    }

    public void setName(UUID id, String name) {
        jdbc.update("UPDATE app_users SET name = ? WHERE id = ?", name, id);
    }

    public void setPicture(UUID id, String pictureUrl) {
        jdbc.update("UPDATE app_users SET picture_url = ? WHERE id = ?", pictureUrl, id);
    }

    public void setStatus(UUID id, AppUserStatus status, boolean active) {
        jdbc.update("UPDATE app_users SET status = ?, active = ? WHERE id = ?", status.name(), active, id);
    }

    public List<String> roles(UUID id) {
        return jdbc.queryForList("SELECT role FROM app_user_roles WHERE user_id = ?", String.class, id);
    }

    public void replaceRoles(UUID id, Collection<String> roles) {
        jdbc.update("DELETE FROM app_user_roles WHERE user_id = ?", id);
        roles.forEach(role -> jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, ?)", id, role));
    }

    /** Account purged: nothing personal stays. The row stays so old parcels still point somewhere. */
    public void anonymise(UUID id) {
        jdbc.update("UPDATE app_users SET name = ?, picture_url = NULL, status = 'DELETED', active = FALSE"
                + " WHERE id = ?", DELETED_NAME, id);
        replaceRoles(id, List.of());
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }
}
