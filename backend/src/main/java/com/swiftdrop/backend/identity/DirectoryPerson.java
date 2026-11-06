package com.swiftdrop.backend.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One person as the auth service's Directory API describes them. Email only when asked for and allowed. */
record DirectoryPerson(UUID id, String name, String picture, AppUserStatus status, boolean active,
                       List<String> roles, Instant lastModified, String email) {

    DirectoryPerson {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
