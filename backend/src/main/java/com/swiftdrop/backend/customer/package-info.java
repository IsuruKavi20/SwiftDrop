/**
 * Customer: profiles, addresses, bookings and tracking.
 * Front door: {@code entity} (used by dispatcher and notification for the shared delivery tables; to be replaced by
 * an API later) and the {@code CustomerStatusChange} event. Everything else is private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Customer")
package com.swiftdrop.backend.customer;
