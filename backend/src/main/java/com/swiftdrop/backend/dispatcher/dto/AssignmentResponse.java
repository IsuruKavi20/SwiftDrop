package com.swiftdrop.backend.dispatcher.dto;

import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.identity.entity.AppUser;

import java.time.Instant;

/** One row of a delivery's courier history: the active assignment has no end; ended ones say who ended them and why. */
public record AssignmentResponse(
        Long assignmentId,
        Long courierId,
        String courierName,
        String assignedByName,
        Instant assignedAt,
        boolean active,
        String endedByName,
        Instant endedAt,
        String endReason) {

    public static AssignmentResponse from(CourierAssignment a) {
        return new AssignmentResponse(a.getId(), a.getCourier().getId(), nameOf(a.getCourier().getUser()),
                nameOf(a.getAssignedBy()), a.getAssignedAt(), a.isActive(),
                a.getEndedBy() == null ? null : nameOf(a.getEndedBy()), a.getEndedAt(), a.getEndReason());
    }

    /** A person's display name; people the auth service has not named (or deleted) still need a label. */
    public static String nameOf(AppUser person) {
        return person.getName().filter(name -> !name.isBlank()).orElse("Unknown person");
    }
}
