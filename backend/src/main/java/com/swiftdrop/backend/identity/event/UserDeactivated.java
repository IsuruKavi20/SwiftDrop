package com.swiftdrop.backend.identity.event;

import java.util.UUID;

/**
 * Published inside SwiftDrop when the auth service disables a person (blocked by an admin). They can no longer log in
 * or be required; e.g. unassign their open deliveries. Listen with {@code @TransactionalEventListener}.
 */
public record UserDeactivated(UUID userId) {
}
