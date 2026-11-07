package com.swiftdrop.backend.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The only code that writes app_users (AppUser itself is @Immutable). Plain SQL that works the same on
 * PostgreSQL and the H2 test database: update the row, insert it if it wasn't there, then replace its roles.
 */
@Component
class AppUserWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    AppUserWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    void save(DirectoryPerson person) {
        boolean deleted = person.status() == AppUserStatus.DELETED;
        String name = deleted ? null : person.name();
        String picture = deleted ? null : person.picture();
        Timestamp modified = person.lastModified() == null ? null : Timestamp.from(person.lastModified());
        Timestamp now = Timestamp.from(clock.instant());

        int updated = jdbc.update("UPDATE app_users SET name = ?, picture_url = ?, status = ?, active = ?,"
                        + " last_modified = ?, synced_at = ? WHERE id = ?",
                name, picture, person.status().name(), person.active(), modified, now, person.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO app_users (id, name, picture_url, status, active, last_modified, synced_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    person.id(), name, picture, person.status().name(), person.active(), modified, now);
        }
        jdbc.update("DELETE FROM app_user_roles WHERE user_id = ?", person.id());
        List<String> roles = deleted ? List.of() : person.roles();
        roles.forEach(role -> jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, ?)", person.id(), role));
    }

    /** Ids whose copy is older than the given time (or never filled in), oldest first. */
    List<UUID> staleIds(java.time.Instant olderThan, int limit) {
        return jdbc.queryForList("SELECT id FROM app_users WHERE status <> 'DELETED'"
                        + " AND (synced_at IS NULL OR synced_at < ?) ORDER BY synced_at NULLS FIRST LIMIT ?",
                UUID.class, Timestamp.from(olderThan), limit);
    }

    /** Touch rows the directory didn't return (unknown to auth) so we don't ask about them every round. */
    void markChecked(List<UUID> ids) {
        Timestamp now = Timestamp.from(clock.instant());
        ids.forEach(id -> jdbc.update("UPDATE app_users SET synced_at = ? WHERE id = ?", now, id));
    }
}
