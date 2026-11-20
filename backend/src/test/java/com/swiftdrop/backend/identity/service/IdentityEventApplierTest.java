package com.swiftdrop.backend.identity.service;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.AppUserStatus;
import com.swiftdrop.backend.identity.event.RoleRevoked;
import com.swiftdrop.backend.identity.event.UserDeactivated;
import com.swiftdrop.backend.identity.event.UserDeleted;
import com.swiftdrop.backend.identity.security.VerifiedSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.swiftdrop.backend.identity.service.IdentityEventApplier.ACCOUNT_DISABLED;
import static com.swiftdrop.backend.identity.service.IdentityEventApplier.ACCOUNT_PURGED;
import static com.swiftdrop.backend.identity.service.IdentityEventApplier.PROFILE_UPDATED;
import static com.swiftdrop.backend.identity.service.IdentityEventApplier.ROLES_CHANGED;
import static com.swiftdrop.backend.identity.service.IdentityEventApplier.USER_REGISTERED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@RecordApplicationEvents
public class IdentityEventApplierTest {

    @Autowired
    private IdentityEventApplier applier;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents moduleEvents;

    private final UUID kasun = UUID.randomUUID();

    @Test
    void aRegisteredPersonIsCopied() {
        applier.apply(event("e1", USER_REGISTERED, 1,
                Map.of("name", "Kasun Silva", "status", "ACTIVE", "roles", List.of("CUSTOMER"))));

        AppUser person = userDirectory.get(kasun).orElseThrow();
        assertThat(person.getName()).contains("Kasun Silva");
        assertThat(person.isActive()).isTrue();
        assertThat(person.hasRole("CUSTOMER")).isTrue();
    }

    @Test
    void sameEventTwiceIsAppliedOnce() {
        registered();
        VerifiedSet rename = event("e2", PROFILE_UPDATED, 2, Map.of("name", "Kasun S."));

        assertThat(applier.apply(rename)).isEqualTo(IdentityEventApplier.Outcome.APPLIED);
        jdbc.update("UPDATE app_users SET name = 'changed meanwhile' WHERE id = ?", kasun);
        assertThat(applier.apply(rename)).isEqualTo(IdentityEventApplier.Outcome.REPEAT);

        assertThat(name()).isEqualTo("changed meanwhile");   // the repeat changed nothing
    }

    @Test
    void version2AfterVersion3IsIgnored() {
        registered();
        applier.apply(event("e3", PROFILE_UPDATED, 3, Map.of("name", "Newest Name")));

        IdentityEventApplier.Outcome late = applier.apply(event("e2", PROFILE_UPDATED, 2, Map.of("name", "Older Name")));

        assertThat(late).isEqualTo(IdentityEventApplier.Outcome.OUTDATED);
        assertThat(name()).isEqualTo("Newest Name");
    }

    @Test
    void onlyTheFieldsInTheEventChange() {
        registered();
        jdbc.update("UPDATE app_users SET picture_url = 'https://lh3.googleusercontent.com/a' WHERE id = ?", kasun);

        applier.apply(event("e2", PROFILE_UPDATED, 2, Map.of("name", "Kasun S.")));

        assertThat(jdbc.queryForObject("SELECT picture_url FROM app_users WHERE id = ?", String.class, kasun))
                .isEqualTo("https://lh3.googleusercontent.com/a");
    }

    @Test
    void rolesAreReplacedWithTheNewList() {
        registered();

        applier.apply(event("e2", ROLES_CHANGED, 2, Map.of("roles", List.of("CUSTOMER", "COURIER"))));

        assertThat(userDirectory.get(kasun).orElseThrow().getRoles()).containsExactlyInAnyOrder("CUSTOMER", "COURIER");
    }

    @Test
    void rolesChangedWithoutRolesRemovesNothing() {
        registered();

        applier.apply(event("e2", ROLES_CHANGED, 2, Map.of()));

        assertThat(userDirectory.get(kasun).orElseThrow().getRoles()).containsExactly("CUSTOMER");
        assertThat(moduleEvents.stream(RoleRevoked.class)).isEmpty();
    }

    @Test
    void anEventCanNeverMakeSomeoneDeletedWithoutAnonymising() {
        applier.apply(event("e1", USER_REGISTERED, 1, Map.of("name", "Kasun Silva", "status", "DELETED")));

        AppUser person = userDirectory.get(kasun).orElseThrow();
        assertThat(person.getStatus()).isEqualTo(AppUserStatus.DISABLED);
        assertThat(person.isActive()).isFalse();
    }

    @Test
    void aDisabledPersonCanNoLongerBeRequired() {
        registered();

        applier.apply(event("e2", ACCOUNT_DISABLED, 2, Map.of()));

        AppUser person = userDirectory.get(kasun).orElseThrow();
        assertThat(person.isActive()).isFalse();
        assertThat(person.getStatus()).isEqualTo(AppUserStatus.DISABLED);
    }

    @Test
    void purgeAnonymisesAndPublishesUserDeleted() {
        registered();
        jdbc.update("UPDATE app_users SET picture_url = 'https://lh3.googleusercontent.com/a' WHERE id = ?", kasun);

        applier.apply(event("e2", ACCOUNT_PURGED, 2, Map.of()));

        AppUser gone = userDirectory.get(kasun).orElseThrow();
        assertThat(gone.getName()).contains("Deleted user");
        assertThat(gone.getPictureUrl()).isEmpty();
        assertThat(gone.getRoles()).isEmpty();
        assertThat(gone.getStatus()).isEqualTo(AppUserStatus.DELETED);
        assertThat(gone.isActive()).isFalse();
        assertThat(moduleEvents.stream(UserDeleted.class)).containsExactly(new UserDeleted(kasun));
    }

    @Test
    void losingCourierPublishesRoleRevoked() {
        registered();
        applier.apply(event("e2", ROLES_CHANGED, 2, Map.of("roles", List.of("CUSTOMER", "COURIER"))));

        applier.apply(event("e3", ROLES_CHANGED, 3, Map.of("roles", List.of("CUSTOMER"))));

        assertThat(moduleEvents.stream(RoleRevoked.class)).containsExactly(new RoleRevoked(kasun, "COURIER"));
    }

    @Test
    void disablingPublishesUserDeactivated() {
        registered();

        applier.apply(event("e2", ACCOUNT_DISABLED, 2, Map.of()));

        assertThat(moduleEvents.stream(UserDeactivated.class)).containsExactly(new UserDeactivated(kasun));
    }

    @Test
    void aRepeatPublishesNothing() {
        registered();
        VerifiedSet disable = event("e2", ACCOUNT_DISABLED, 2, Map.of());
        applier.apply(disable);
        applier.apply(disable);

        assertThat(moduleEvents.stream(UserDeactivated.class)).hasSize(1);
    }

    @Test
    void anEventAboutSomeoneNewCreatesTheirRow() {
        applier.apply(event("e5", ROLES_CHANGED, 5, Map.of("roles", List.of("COURIER"))));

        assertThat(jdbc.queryForObject("SELECT last_event_version FROM app_users WHERE id = ?", Long.class, kasun))
                .isEqualTo(5);
    }

    private void registered() {
        applier.apply(event("e1", USER_REGISTERED, 1,
                Map.of("name", "Kasun Silva", "status", "ACTIVE", "roles", List.of("CUSTOMER"))));
    }

    private String name() {
        return jdbc.queryForObject("SELECT name FROM app_users WHERE id = ?", String.class, kasun);
    }

    private VerifiedSet event(String jti, String type, long version, Map<String, Object> data) {
        // Unique jtis per test run (the table is shared by the whole test database)
        return new VerifiedSet(kasun + "-" + jti, kasun, type, data, version);
    }
}
