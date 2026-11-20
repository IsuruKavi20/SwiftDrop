/**
 * Identity: the people of SwiftDrop (a read-only copy from the auth service).
 * Front door for other modules: {@code entity} (AppUser), {@code UserDirectory}, {@code event}, {@code exception}.
 * Everything else is private. See docs/USERS.md and docs/MODULES.md.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.swiftdrop.backend.identity;
