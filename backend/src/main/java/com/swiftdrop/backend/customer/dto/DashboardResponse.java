package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository.StatusCount;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The customer dashboard: delivery counts and the most recent deliveries. */
public record DashboardResponse(
        long totalDeliveries,
        long activeDeliveries,
        Map<DeliveryStatus, Long> countsByStatus,
        List<DeliverySummaryResponse> recentDeliveries) {

    /** Statuses where the delivery is still on its way. */
    private static final Set<DeliveryStatus> ACTIVE = Set.of(DeliveryStatus.CONFIRMED, DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT, DeliveryStatus.OUT_FOR_DELIVERY);

    /** Every status appears in countsByStatus, with 0 where the customer has none, so the frontend need not check. */
    public static DashboardResponse of(List<StatusCount> counts, List<DeliverySummaryResponse> recentDeliveries) {
        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            byStatus.put(status, 0L);
        }
        counts.forEach(count -> byStatus.put(count.getStatus(), count.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long active = ACTIVE.stream().mapToLong(byStatus::get).sum();
        return new DashboardResponse(total, active, byStatus, recentDeliveries);
    }
}
