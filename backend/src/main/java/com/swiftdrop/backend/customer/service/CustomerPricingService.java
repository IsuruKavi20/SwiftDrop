package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.FeeEstimateResponse;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Provisional Sri Lankan demo pricing. The Dispatcher/Admin pricing implementation should replace these constants. */
@Service
public class CustomerPricingService {
    public FeeEstimateResponse estimate(CreateDeliveryRequest request) {
        boolean local = request.pickup().district().trim().equalsIgnoreCase(request.destination().district().trim());
        BigDecimal base = local ? new BigDecimal("350.00") : new BigDecimal("650.00");
        BigDecimal totalWeight = request.parcels().stream().map(p -> p.weightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = totalWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weight = extra.multiply(new BigDecimal("120.00")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = switch (request.deliveryType()) {
            case STANDARD -> BigDecimal.ZERO;
            case EXPRESS -> new BigDecimal("350.00");
            case SAME_DAY -> new BigDecimal("650.00");
        };
        BigDecimal total = base.add(weight).add(service).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Parcel weight exceeds supported quote range");
        }
        return new FeeEstimateResponse("LKR", base, weight, service, total,
                "Illustrative district and weight pricing; admin-managed tariff integration is pending.");
    }
}
