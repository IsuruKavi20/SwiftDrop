package com.swiftdrop.backend.courier.dto;

/**
 * Summary metrics displayed on the Courier Dashboard.
 */
public record CourierDashboardResponse(
        long assignedToday,
        long inProgress,
        long delivered,
        long failed,
        int completionPercentage
) {
}

