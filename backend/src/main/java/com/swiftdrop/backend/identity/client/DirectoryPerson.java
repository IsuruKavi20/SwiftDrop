package com.swiftdrop.backend.identity.client;

import com.swiftdrop.backend.identity.entity.AppUserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One person as the auth service's Directory API describes them. Email only when asked for and allowed. */
public record DirectoryPerson(UUID id, String name, String picture, AppUserStatus status, boolean active,
                       List<String> roles, Instant lastModified, String email) {

    public DirectoryPerson {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
