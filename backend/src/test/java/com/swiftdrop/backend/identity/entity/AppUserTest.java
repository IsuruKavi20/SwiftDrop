package com.swiftdrop.backend.identity.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests: the local copy of a person can't be changed from outside, and missing data stays "unknown". */
class AppUserTest {

    @Test
    void rolesCanNotBeChangedFromOutside() {
        Set<String> roles = new HashSet<>(Set.of("CUSTOMER"));
        AppUser person = new AppUser(UUID.randomUUID(), "Kasun", AppUserStatus.ACTIVE, true, roles);

        roles.add("ADMIN");   // changing the caller's set later must not give the person ADMIN

        assertThat(person.hasRole("ADMIN")).isFalse();
        assertThatThrownBy(() -> person.getRoles().add("ADMIN")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingDataIsEmptyNotNull() {
        AppUser person = new AppUser(UUID.randomUUID(), null, AppUserStatus.PENDING_SYNC, false, Set.of());

        assertThat(person.getName()).isEmpty();
        assertThat(person.getPictureUrl()).isEmpty();
        assertThat(person.getLastModified()).isNull();
        assertThat(person.getSyncedAt()).isNull();
        assertThat(person.isActive()).isFalse();
        assertThat(person.getStatus()).isEqualTo(AppUserStatus.PENDING_SYNC);
    }
}
