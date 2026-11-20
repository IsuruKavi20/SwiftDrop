package com.swiftdrop.backend.identity.event;

import java.util.UUID;

/**
 * Published inside SwiftDrop when a person loses a role at the auth service (e.g. no longer COURIER): one event per
 * role that disappeared. Listen with {@code @TransactionalEventListener}.
 */
public record RoleRevoked(UUID userId, String role) {
}
