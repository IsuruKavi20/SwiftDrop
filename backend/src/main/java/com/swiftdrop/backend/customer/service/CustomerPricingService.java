package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.FeeEstimateResponse;
import com.swiftdrop.backend.pricing.Tariff;
import com.swiftdrop.backend.pricing.TariffSource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sri Lankan delivery quotes with the tariff an admin published (pricing module), or the server's settings while
 * none is published. All amounts are server-owned. The customer cannot alter a quote by changing frontend values,
 * and a booking stores its fee, so a later tariff never reprices it.
 */
@Service
public class CustomerPricingService {
    private final TariffSource tariffs;

    public CustomerPricingService(TariffSource tariffs) {
        this.tariffs = tariffs;
    }

    public FeeEstimateResponse estimate(CreateDeliveryRequest request) {
        Tariff tariff = tariffs.current();
        boolean local = request.pickup().district().trim().equalsIgnoreCase(request.destination().district().trim());
        BigDecimal base = local ? tariff.sameDistrictBase() : tariff.otherDistrictBase();
        BigDecimal totalWeight = request.parcels().stream().map(p -> p.weightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = totalWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weight = extra.multiply(tariff.additionalKg()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = switch (request.deliveryType()) {
            case STANDARD -> BigDecimal.ZERO;
            case EXPRESS -> tariff.expressSurcharge();
            case SAME_DAY -> tariff.sameDaySurcharge();
        };
        BigDecimal total = base.add(weight).add(service).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Parcel weight exceeds supported quote range");
        }
        String rates = tariff.isDefault() ? "Standard SwiftDrop rates" : "SwiftDrop rates (version " + tariff.version() + ")";
        return new FeeEstimateResponse("LKR", base, weight, service, total,
                rates + ": district base + additional weight + service surcharge.");
    }
}
