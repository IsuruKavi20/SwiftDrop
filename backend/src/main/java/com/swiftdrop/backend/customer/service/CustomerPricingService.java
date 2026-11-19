package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.customer.dto.FeeEstimateResponse;
import com.swiftdrop.backend.pricing.DistanceSource;
import com.swiftdrop.backend.pricing.DistanceSource.RouteDistance;
import com.swiftdrop.backend.pricing.Tariff;
import com.swiftdrop.backend.pricing.TariffSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * All amounts and the distance are worked out on the server: the customer cannot alter a quote by changing frontend
 * values, and a booking stores its fee, so a later tariff never reprices it.
 */
@Service
public class CustomerPricingService {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");

    private final TariffSource tariffs;
    private final DistanceSource distances;

    public CustomerPricingService(TariffSource tariffs, DistanceSource distances) {
        this.tariffs = tariffs;
        this.distances = distances;
    }

    public FeeEstimateResponse estimate(CreateDeliveryRequest request) {
        Tariff tariff = tariffs.current();
        String from = address(request.pickup());
        String to = address(request.destination());
        Optional<RouteDistance> route = distances.drivingDistance(from, to);

        BigDecimal base;
        BigDecimal distanceCharge;
        BigDecimal km = null;
        String distancePart;
        if (route.isPresent()) {
            km = route.get().km().setScale(1, RoundingMode.HALF_UP);            BigDecimal chargedKm = km.subtract(tariff.includedKm()).max(BigDecimal.ZERO);
            base = tariff.distanceBase();
            distanceCharge = chargedKm.multiply(tariff.perKm()).setScale(2, RoundingMode.HALF_UP);
            distancePart = km.toPlainString() + " km by road: base fee for the first "
                    + tariff.includedKm().stripTrailingZeros().toPlainString() + " km + LKR "
                    + tariff.perKm().toPlainString() + " per km after that";
        } else {
            boolean local = request.pickup().district().trim().equalsIgnoreCase(request.destination().district().trim());
            base = local ? tariff.sameDistrictBase() : tariff.otherDistrictBase();
            distanceCharge = BigDecimal.ZERO.setScale(2);
            distancePart = "district base";
        }

        BigDecimal totalWeight = request.parcels().stream().map(p -> p.weightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = totalWeight.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weight = extra.multiply(tariff.additionalKg()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal service = switch (request.deliveryType()) {
            case STANDARD -> BigDecimal.ZERO;
            case EXPRESS -> tariff.expressSurcharge();
            case SAME_DAY -> tariff.sameDaySurcharge();
        };
        BigDecimal total = base.add(distanceCharge).add(weight).add(service).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalArgumentException("Parcel weight exceeds supported quote range");
        }
        String rates = tariff.isDefault() ? "Standard SwiftDrop rates" : "SwiftDrop rates (version " + tariff.version() + ")";
        return new FeeEstimateResponse("LKR", km, from, to, base, distanceCharge, weight, service, total,
                rates + ": " + distancePart + " + additional weight + service surcharge.");
    }

    /** The address as Google reads it: "1 Main St, Colombo, Colombo District, 00100, Sri Lanka". */
    static String address(DeliveryAddressRequest address) {
        return Stream.of(address.street(), address.city(), district(address.district()), address.postalCode(), "Sri Lanka")
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private static String district(String district) {
        return district == null || district.isBlank() ? null : district.trim() + " District";
    }
}
