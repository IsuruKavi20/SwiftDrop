package com.swiftdrop.backend.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A person from the SwiftDrop auth service, as a read-only copy. Other modules link to it
 * ({@code @ManyToOne AppUser courier}) but can never change it: only the auth service changes people,
 * and only the identity module refreshes this copy.
 */
@Entity
@Immutable
@Table(name = "app_users")
public class AppUser {

    @Id
    private UUID id;

    @Column(length = 200)
    private String name;

    @Column(name = "picture_url", length = 2048)
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppUserStatus status;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_modified")
    private Instant lastModified;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Immutable
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 40)
    private Set<String> roles = Set.of();

    protected AppUser() {
        // for JPA
    }

    /** Only for tests in this package (see TestPeople); real rows come from the auth service via AppUserWriter. */
    AppUser(UUID id, String name, AppUserStatus status, boolean active, Set<String> roles) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.active = active;
        this.roles = Set.copyOf(roles);
    }

    public UUID getId() { return id; }

    /** Empty when we don't know it yet or the person deleted their account. */
    public Optional<String> getName() { return Optional.ofNullable(name); }

    public Optional<String> getPictureUrl() { return Optional.ofNullable(pictureUrl); }

    public AppUserStatus getStatus() { return status; }

    /** True only when the auth service says this person may be used (not blocked, not deleted). */
    public boolean isActive() { return active; }

    public boolean hasRole(String role) { return roles.contains(role); }

    public Set<String> getRoles() { return Set.copyOf(roles); }

    public Instant getLastModified() { return lastModified; }

    public Instant getSyncedAt() { return syncedAt; }
}
