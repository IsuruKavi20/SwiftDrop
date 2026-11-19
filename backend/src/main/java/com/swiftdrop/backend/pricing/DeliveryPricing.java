package com.swiftdrop.backend.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** The published tariff calculation shared by Customer and Merchant bookings. */
@Service
public class DeliveryPricing {
    private final TariffSource tariffs;

    public DeliveryPricing(TariffSource tariffs) {
        this.tariffs = tariffs;
    }

    public Quote estimate(String pickupDistrict, String destinationDistrict, String deliveryType,
                          List<BigDecimal> weights) {
        Tariff tariff = tariffs.current();
        boolean local = pickupDistrict.trim().equalsIgnoreCase(destinationDistrict.trim());
        BigDecimal base = local ? tariff.sameDistrictBase() : tariff.otherDistrictBase();
        BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = totalWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weight = extra.multiply(tariff.additionalKg()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = switch (deliveryType) {
            case "STANDARD" -> BigDecimal.ZERO;
            case "EXPRESS" -> tariff.expressSurcharge();
            case "SAME_DAY" -> tariff.sameDaySurcharge();
            default -> throw new IllegalArgumentException("Unknown delivery service");
        };
        BigDecimal total = base.add(weight).add(service).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Parcel weight exceeds supported quote range");
        }
        String rates = tariff.isDefault() ? "Standard SwiftDrop rates" : "SwiftDrop rates (version " + tariff.version() + ")";
        return new Quote("LKR", base, weight, service, total,
                rates + ": district base + additional weight + service surcharge.");
    }

    public record Quote(String currency, BigDecimal baseFee, BigDecimal weightCharge,
                        BigDecimal serviceCharge, BigDecimal totalFee, String explanation) {}
}
