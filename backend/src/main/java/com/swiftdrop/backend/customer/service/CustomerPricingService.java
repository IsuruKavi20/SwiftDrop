package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.FeeEstimateResponse;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Configurable provisional Sri Lankan tariffs, until the Dispatcher/Admin pricing module supplies official rates.
 * All amounts are server-owned. The customer cannot alter a quote by changing frontend values.
 */
@Service
public class CustomerPricingService {
    @Value("${swiftdrop.customer.pricing.same-district-base:350.00}")
    private BigDecimal sameDistrictBase = new BigDecimal("350.00");
    @Value("${swiftdrop.customer.pricing.other-district-base:650.00}")
    private BigDecimal otherDistrictBase = new BigDecimal("650.00");
    @Value("${swiftdrop.customer.pricing.additional-kg:120.00}")
    private BigDecimal additionalKg = new BigDecimal("120.00");
    @Value("${swiftdrop.customer.pricing.express-surcharge:350.00}")
    private BigDecimal expressSurcharge = new BigDecimal("350.00");
    @Value("${swiftdrop.customer.pricing.same-day-surcharge:650.00}")
    private BigDecimal sameDaySurcharge = new BigDecimal("650.00");

    public FeeEstimateResponse estimate(CreateDeliveryRequest request) {
        boolean local = request.pickup().district().trim().equalsIgnoreCase(request.destination().district().trim());
        BigDecimal base = local ? sameDistrictBase : otherDistrictBase;
        BigDecimal totalWeight = request.parcels().stream().map(p -> p.weightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = totalWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weight = extra.multiply(additionalKg).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = switch (request.deliveryType()) {
            case STANDARD -> BigDecimal.ZERO;
            case EXPRESS -> expressSurcharge;
            case SAME_DAY -> sameDaySurcharge;
        };
        BigDecimal total = base.add(weight).add(service).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Parcel weight exceeds supported quote range");
        }
        return new FeeEstimateResponse("LKR", base, weight, service, total,
                "Configurable provisional tariff: district base + additional weight + service surcharge. Final admin pricing integration pending.");
    }
}
