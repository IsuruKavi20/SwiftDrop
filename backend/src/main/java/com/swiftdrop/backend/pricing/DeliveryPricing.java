package com.swiftdrop.backend.pricing;

import com.swiftdrop.backend.pricing.DistanceSource.RouteDistance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sri Lankan delivery quotes with the tariff an admin published (pricing module), or the server's settings while
 * none is published.
 * <p>
 * Fee = base fee (covers the first km) + per-km rate × the road km after that + additional weight + service
 * surcharge. The road distance is the driving route Google Maps finds between the pickup and destination addresses.
 * When Google can't give one (no API key, unreachable, an address it can't route), the district base (same district
 * or not) replaces the distance part, so a quote never fails because of Google.
 * <p>
 * All amounts and the distance are worked out on the server: the caller cannot alter a quote by changing frontend
 * values, and a booking stores its fee, so a later tariff never reprices it.
 */
@Service
public class DeliveryPricing {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");

    private final TariffSource tariffs;
    private final DistanceSource distances;

    public DeliveryPricing(TariffSource tariffs, DistanceSource distances) {
        this.tariffs = tariffs;
        this.distances = distances;
    }

    public Quote estimate(Address pickup, Address destination, String serviceType, List<BigDecimal> weights) {
        Tariff tariff = tariffs.current();
        String from = address(pickup);
        String to = address(destination);
        Optional<RouteDistance> route = distances.drivingDistance(from, to);

        BigDecimal base;
        BigDecimal distanceCharge;
        BigDecimal km = null;
        String distancePart;
        if (route.isPresent()) {
            km = route.get().km().setScale(1, RoundingMode.HALF_UP);
            BigDecimal chargedKm = km.subtract(tariff.includedKm()).max(BigDecimal.ZERO);
            base = tariff.distanceBase();
            distanceCharge = chargedKm.multiply(tariff.perKm()).setScale(2, RoundingMode.HALF_UP);
            distancePart = km.toPlainString() + " km by road: base fee for the first "
                    + tariff.includedKm().stripTrailingZeros().toPlainString() + " km + LKR "
                    + tariff.perKm().toPlainString() + " per km after that";
        } else {
            boolean local = pickup.district().trim().equalsIgnoreCase(destination.district().trim());
            base = local ? tariff.sameDistrictBase() : tariff.otherDistrictBase();
            distanceCharge = BigDecimal.ZERO.setScale(2);
            distancePart = "district base";
        }

        BigDecimal totalWeight = weights.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = totalWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weight = extra.multiply(tariff.additionalKg()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = switch (serviceType) {
            case "STANDARD" -> BigDecimal.ZERO;
            case "EXPRESS" -> tariff.expressSurcharge();
            case "SAME_DAY" -> tariff.sameDaySurcharge();
            default -> throw new IllegalArgumentException("Unknown delivery service");
        };
        BigDecimal total = base.add(distanceCharge).add(weight).add(service).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalArgumentException("Parcel weight exceeds supported quote range");
        }
        String rates = tariff.isDefault() ? "Standard SwiftDrop rates" : "SwiftDrop rates (version " + tariff.version() + ")";
        return new Quote("LKR", km, from, to, base, distanceCharge, weight, service, total,
                rates + ": " + distancePart + " + additional weight + service surcharge.");
    }

    /** The address as Google reads it: "1 Main St, Colombo, Colombo District, 00100, Sri Lanka". */
    private static String address(Address address) {
        return Stream.of(address.street(), address.city(), district(address.district()), address.postalCode(), "Sri Lanka")
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private static String district(String district) {
        return district == null || district.isBlank() ? null : district.trim() + " District";
    }

    public record Address(String street, String city, String district, String postalCode) {}

    public record Quote(String currency, BigDecimal distanceKm, String routeFrom, String routeTo,
                        BigDecimal baseFee, BigDecimal distanceCharge, BigDecimal weightCharge,
                        BigDecimal serviceCharge, BigDecimal totalFee, String explanation) {}
}
