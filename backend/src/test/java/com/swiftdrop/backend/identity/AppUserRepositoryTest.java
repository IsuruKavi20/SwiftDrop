package com.swiftdrop.backend.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AppUserRepositoryTest {

    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID BLOCKED_COURIER = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void insertPeople() {
        // The sync (step 2) will write these rows; here we write them directly with SQL
        insert(COURIER, "Kasun Silva", "ACTIVE", true, "COURIER");
        insert(BLOCKED_COURIER, "Blocked Courier", "LOCKED", false, "COURIER");
        insert(CUSTOMER, "Nimal Perera", "ACTIVE", true, "CUSTOMER");
    }

    @Test
    void findsAPersonWithTheirRoles() {
        AppUser kasun = users.findById(COURIER).orElseThrow();

        assertThat(kasun.getName()).contains("Kasun Silva");
        assertThat(kasun.isActive()).isTrue();
        assertThat(kasun.hasRole("COURIER")).isTrue();
        assertThat(kasun.hasRole("ADMIN")).isFalse();
    }

    @Test
    void listsOnlyActivePeopleWithTheRole() {
        List<AppUser> couriers = users.findByRolesContainingAndActiveTrue("COURIER");

        assertThat(couriers).extracting(AppUser::getId).containsExactly(COURIER);
    }

    @Test
    void findsManyAtOnce() {
        assertThat(users.findByIdIn(List.of(COURIER, CUSTOMER))).hasSize(2);
    }

    @Test
    void aPlaceholderHasNoNameYet() {
        UUID unknown = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, status) VALUES (?, 'PENDING_SYNC')", unknown);

        AppUser placeholder = users.findById(unknown).orElseThrow();

        assertThat(placeholder.getName()).isEmpty();
        assertThat(placeholder.isActive()).isFalse();
        assertThat(placeholder.getStatus()).isEqualTo(AppUserStatus.PENDING_SYNC);
    }

    private void insert(UUID id, String name, String status, boolean active, String role) {
        jdbc.update("INSERT INTO app_users (id, name, status, active) VALUES (?, ?, ?, ?)", id, name, status, active);
        jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, ?)", id, role);
    }
}
