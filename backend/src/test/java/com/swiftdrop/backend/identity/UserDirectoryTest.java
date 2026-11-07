package com.swiftdrop.backend.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class UserDirectoryTest {

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private DirectoryRefresh refresh;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private DirectoryClient directory;

    @Test
    void copiesANewPersonTheFirstTimeThenUsesTheCopy() {
        UUID kasun = UUID.randomUUID();
        when(directory.get(kasun, false)).thenReturn(Optional.of(person(kasun, "Kasun Silva", AppUserStatus.ACTIVE, true)));

        AppUser first = userDirectory.require(kasun);
        AppUser second = userDirectory.require(kasun);

        assertThat(first.getName()).contains("Kasun Silva");
        assertThat(second.hasRole("COURIER")).isTrue();
        verify(directory, org.mockito.Mockito.times(1)).get(kasun, false);   // only the first call asked auth
    }

    @Test
    void aBlockedPersonCannotBeRequired() {
        UUID blocked = UUID.randomUUID();
        when(directory.get(blocked, false)).thenReturn(Optional.of(person(blocked, "Blocked", AppUserStatus.LOCKED, false)));

        assertThatThrownBy(() -> userDirectory.require(blocked)).isInstanceOf(UnknownUserException.class);
        assertThat(userDirectory.get(blocked)).isPresent();   // still readable (old parcels show the name)
    }

    @Test
    void anUnknownPersonCannotBeRequired() {
        UUID nobody = UUID.randomUUID();
        when(directory.get(nobody, false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDirectory.require(nobody)).isInstanceOf(UnknownUserException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE id = ?", Long.class, nobody)).isZero();
    }

    @Test
    void aPlaceholderIsFilledInFromAuth() {
        UUID old = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, status) VALUES (?, 'PENDING_SYNC')", old);
        when(directory.get(old, false)).thenReturn(Optional.of(person(old, "Nimal Perera", AppUserStatus.ACTIVE, true)));

        assertThat(userDirectory.require(old).getName()).contains("Nimal Perera");
    }

    @Test
    void theRefreshAppliesBlocksAndDeletions() {
        UUID kasun = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        insertStale(kasun, "Kasun Silva");
        insertStale(gone, "Gone Person");
        when(directory.lookup(any())).thenReturn(List.of(
                person(kasun, "Kasun Silva", AppUserStatus.LOCKED, false),
                person(gone, "Gone Person", AppUserStatus.DELETED, false)));

        refresh.refreshStale();

        assertThat(userDirectory.get(kasun).orElseThrow().isActive()).isFalse();
        AppUser deleted = userDirectory.get(gone).orElseThrow();
        assertThat(deleted.getName()).as("a deleted person's name is wiped").isEmpty();
        assertThat(deleted.getRoles()).isEmpty();
        verify(directory, never()).get(any(), anyBoolean());   // both were already copied
    }

    @Test
    void listsActiveCouriersFromTheCopy() {
        UUID kasun = UUID.randomUUID();
        when(directory.get(kasun, false)).thenReturn(Optional.of(person(kasun, "Kasun Silva", AppUserStatus.ACTIVE, true)));
        userDirectory.require(kasun);

        assertThat(userDirectory.listByRole("COURIER")).extracting(AppUser::getId).contains(kasun);
    }

    @Test
    void theCallerIsCopiedFromTheirTokenWithoutAskingAuth() {
        UUID nimal = UUID.randomUUID();
        org.springframework.security.oauth2.jwt.Jwt token = org.springframework.security.oauth2.jwt.Jwt
                .withTokenValue("t").header("alg", "RS256").subject(nimal.toString())
                .claim("name", "Nimal Perera").claim("roles", List.of("CUSTOMER")).build();

        AppUser caller = userDirectory.caller(token);

        assertThat(caller.getName()).contains("Nimal Perera");
        assertThat(caller.hasRole("CUSTOMER")).isTrue();
        assertThat(caller.isActive()).isTrue();
        verify(directory, never()).get(any(), anyBoolean());
    }

    private void insertStale(UUID id, String name) {
        jdbc.update("INSERT INTO app_users (id, name, status, active, synced_at) VALUES (?, ?, 'ACTIVE', TRUE, ?)",
                id, name, java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")));
        jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, 'COURIER')", id);
    }

    private static DirectoryPerson person(UUID id, String name, AppUserStatus status, boolean active) {
        return new DirectoryPerson(id, name, null, status, active, List.of("COURIER"),
                Instant.parse("2026-11-01T10:00:00Z"), null);
    }
}
