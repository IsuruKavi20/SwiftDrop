package com.swiftdrop.backend.identity.service;

import com.swiftdrop.backend.identity.client.DirectoryClient;
import com.swiftdrop.backend.identity.client.DirectoryPerson;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.AppUserStatus;
import com.swiftdrop.backend.identity.exception.UnknownUserException;
import com.swiftdrop.backend.identity.repository.AppUserRepository;
import com.swiftdrop.backend.identity.repository.AppUserWriter;
import jakarta.persistence.EntityManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one thing other modules use to find people. Reads the local copy first (fast, works while auth is down);
 * asks the auth service only for someone we haven't copied yet.
 *
 * <pre>{@code
 * AppUser courier = userDirectory.require(courierId);   // exists and may be used, or UnknownUserException
 * }</pre>
 */
@Service
@org.springframework.modulith.NamedInterface("directory")
public class UserDirectory {

    private final AppUserRepository users;
    private final AppUserWriter writer;
    private final DirectoryClient directory;
    private final EntityManager entityManager;

    UserDirectory(AppUserRepository users, AppUserWriter writer, DirectoryClient directory,
                  EntityManager entityManager) {
        this.users = users;
        this.writer = writer;
        this.directory = directory;
        this.entityManager = entityManager;
    }

    /** The person, copying them from auth the first time. Empty if auth doesn't know them. */
    @Transactional
    public Optional<AppUser> get(UUID id) {
        Optional<AppUser> local = users.findById(id);
        if (local.isPresent() && local.get().getStatus() != AppUserStatus.PENDING_SYNC) {
            return local;
        }
        Instant lookedUpAt = Instant.now();
        Optional<DirectoryPerson> remote = directory.get(id, false);
        if (remote.isEmpty()) {
            return Optional.empty();
        }
        writer.saveFromDirectory(remote.get(), lookedUpAt);
        // The writer uses SQL, so JPA's remembered placeholder is now old: forget it and read the fresh row
        local.ifPresent(entityManager::detach);
        return users.findById(id);
    }

    /** The person if they exist and may be used (not blocked or deleted); otherwise UnknownUserException. */
    @Transactional
    public AppUser require(UUID id) {
        return get(id).filter(AppUser::isActive).orElseThrow(() -> new UnknownUserException(id));
    }

    /**
     * The person calling us, from their access token. The first time we see them (or while their row is still a
     * placeholder), they are copied from the token's own claims, so this works even when the directory is off.
     * A valid token means auth considers them usable right now; the refresh fills in the rest (photo etc.).
     */
    @Transactional
    public AppUser caller(Jwt token) {
        UUID id = UUID.fromString(token.getSubject());
        Optional<AppUser> local = users.findById(id);
        String tokenName = token.getClaimAsString("name");
        if (local.isPresent() && local.get().getStatus() != AppUserStatus.PENDING_SYNC) {
            // Copied from a token that had no name (e.g. right after sign-up): fill it in once a token carries one
            if (local.get().getName().isEmpty() && tokenName != null && !tokenName.isBlank()
                    && local.get().getStatus() != AppUserStatus.DELETED) {
                writer.setName(id, tokenName.trim());
                entityManager.detach(local.get());
                return users.findById(id).orElseThrow();
            }
            return local.get();
        }
        List<String> roles = token.getClaimAsStringList("roles");
        writer.save(new DirectoryPerson(id, tokenName, null, AppUserStatus.ACTIVE, true,
                roles, null, null));
        local.ifPresent(entityManager::detach);
        return users.findById(id).orElseThrow();
    }

    /** Active people with this role, from the local copy, e.g. listByRole("COURIER"). */
    @Transactional(readOnly = true)
    public List<AppUser> listByRole(String role) {
        return users.findByRolesContainingAndActiveTrue(role);
    }

    /** Asked live, never stored: only call it when you are about to send an email. */
    public Optional<String> email(UUID id) {
        return directory.get(id, true).map(DirectoryPerson::email);
    }
}
