package com.swiftdrop.backend.delivery;

import java.util.UUID;

/**
 * A parcel delivery assigned to a courier. {@code courierId} is the courier's user id in the SwiftDrop auth service
 * (the "sub" claim of their token). Example model for the auth integration; the delivery module will replace it.
 */
public record Delivery(UUID id, String parcelReference, UUID courierId, String status) {
}
