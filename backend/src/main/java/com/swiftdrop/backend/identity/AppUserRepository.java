package com.swiftdrop.backend.identity;

import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only on purpose: it extends {@link Repository}, not JpaRepository, so there is no save() or delete().
 * Package-private: other modules use the UserDirectory service (step 2), never this repository.
 */
interface AppUserRepository extends Repository<AppUser, UUID> {

    Optional<AppUser> findById(UUID id);

    List<AppUser> findByIdIn(Collection<UUID> ids);

    List<AppUser> findByRolesContainingAndActiveTrue(String role);
}
