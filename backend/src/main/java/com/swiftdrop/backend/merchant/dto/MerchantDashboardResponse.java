package com.swiftdrop.backend.merchant.dto;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository.StatusCount;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Summary for the Merchant Dashboard: business identity, order totals by lifecycle status, and recent orders.
 */
public record MerchantDashboardResponse(
        String businessName,
        boolean profileComplete,
        long totalOrders,
        long pendingOrders,
        long inTransitOrders,
        long deliveredOrders,
        Map<DeliveryStatus, Long> countsByStatus,
        List<MerchantDeliverySummaryResponse> recentOrders) {

    private static final Set<DeliveryStatus> IN_TRANSIT_STATUSES = Set.of(
            DeliveryStatus.CONFIRMED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.OUT_FOR_DELIVERY);

    public static MerchantDashboardResponse of(Merchant merchant, List<StatusCount> counts, List<MerchantDeliverySummaryResponse> recentOrders) {
        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            byStatus.put(status, 0L);
        }
        counts.forEach(count -> byStatus.put(count.getStatus(), count.getTotal()));

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long pending = byStatus.getOrDefault(DeliveryStatus.PENDING, 0L);
        long inTransit = IN_TRANSIT_STATUSES.stream().mapToLong(byStatus::get).sum();
        long delivered = byStatus.getOrDefault(DeliveryStatus.DELIVERED, 0L);

        return new MerchantDashboardResponse(
                merchant.getBusinessName(),
                merchant.isComplete(),
                total,
                pending,
                inTransit,
                delivered,
                byStatus,
                recentOrders);
    }
}
