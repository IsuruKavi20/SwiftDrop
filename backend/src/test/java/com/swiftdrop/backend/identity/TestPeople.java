package com.swiftdrop.backend.identity;

import jakarta.persistence.EntityManager;

import java.util.Set;
import java.util.UUID;

/** People for tests in any module: an active user with a name and one role. */
public final class TestPeople {

    private TestPeople() {
    }

    /** Not saved: for plain unit tests that never touch the database. */
    public static AppUser unsaved(String name, String role) {
        return new AppUser(UUID.randomUUID(), name, AppUserStatus.ACTIVE, true, Set.of(role));
    }

    /** Saved in the test database, so foreign keys to app_users accept it. */
    public static AppUser saved(EntityManager em, UUID id, String name, String role) {
        AppUser person = new AppUser(id, name, AppUserStatus.ACTIVE, true, Set.of(role));
        em.persist(person);
        em.flush();
        return person;
    }

    public static AppUser saved(EntityManager em, String name, String role) {
        return saved(em, UUID.randomUUID(), name, role);
    }
}
