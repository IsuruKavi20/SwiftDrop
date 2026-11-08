package com.swiftdrop.backend.identity.event;

import java.util.UUID;

/**
 * Published inside SwiftDrop when a person deleted their account at the auth service (account purged). If your module
 * stores personal data about people (phone, addresses, notes), erase or anonymise that person's rows.
 * Listen with {@code @TransactionalEventListener} so you only act once the change is really saved.
 */
public record UserDeleted(UUID userId) {
}
