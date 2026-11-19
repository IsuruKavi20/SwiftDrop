package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.*;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.pricing.DeliveryPricing;
import com.swiftdrop.backend.pricing.DistanceSource;
import com.swiftdrop.backend.pricing.DistanceSource.RouteDistance;
import com.swiftdrop.backend.pricing.Tariff;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Fee is displayed before booking and saved unchanged on confirmation. */
class CustomerPricingServiceTest {
    /** Google can't give a distance (no key, unreachable…): the district bases apply. */
    private static final DistanceSource NO_DISTANCE = (from, to) -> Optional.empty();

    private final CustomerPricingService pricing = pricing(NO_DISTANCE);

    private static CustomerPricingService pricing(DistanceSource distances) {
        return new CustomerPricingService(new DeliveryPricing(Tariff::defaults, distances));
    }

    private static DistanceSource road(String km) {
        return (from, to) -> Optional.of(new RouteDistance(new BigDecimal(km), Duration.ofHours(3)));
    }

    private CreateDeliveryRequest booking(String origin, String destination, DeliveryType service) {
        var pickup=new DeliveryAddressRequest("Sender","0771234567","Main Road","Colombo",origin,"00100");
        var dropoff=new DeliveryAddressRequest("Recipient","0711234567","Lake Road","Kandy",destination,"20000");
        var parcel=new ParcelRequest("Books",new BigDecimal("2.50"),new BigDecimal("30"),
                new BigDecimal("20"),new BigDecimal("10"),null);
        return new CreateDeliveryRequest(service,pickup,dropoff,List.of(parcel),null);
    }

    @Test void distanceDecidesTheFee() {
        var estimate = pricing(road("115.4"))
                .estimate(booking("Colombo","Kandy",DeliveryType.EXPRESS));
        assertEquals(0,new BigDecimal("115.4").compareTo(estimate.distanceKm()));
        assertEquals("Main Road, Colombo, Colombo District, 00100, Sri Lanka", estimate.routeFrom());
        assertEquals("Lake Road, Kandy, Kandy District, 20000, Sri Lanka", estimate.routeTo());
        assertEquals(0,new BigDecimal("250.00").compareTo(estimate.baseFee()));
        // (115.4 - 5) km × LKR 6
        assertEquals(0,new BigDecimal("662.40").compareTo(estimate.distanceCharge()));
        assertEquals(0,new BigDecimal("180.00").compareTo(estimate.weightCharge()));
        assertEquals(0,new BigDecimal("350.00").compareTo(estimate.serviceCharge()));
        assertEquals(0,new BigDecimal("1442.40").compareTo(estimate.totalFee()));
        assertEquals("Standard SwiftDrop rates: 115.4 km by road: base fee for the first 5 km + LKR 6.00 per km after that"
                + " + additional weight + service surcharge.", estimate.explanation());
    }

    @Test void shortTripsPayOnlyTheBaseFee() {
        var estimate = pricing(road("3.2"))
                .estimate(booking("Colombo","Colombo",DeliveryType.STANDARD));
        assertEquals(0,BigDecimal.ZERO.compareTo(estimate.distanceCharge()));
        assertEquals(0,new BigDecimal("430.00").compareTo(estimate.totalFee())); // 250 + 1.5 kg × 120
    }

    @Test void longerRoutesCostMore() {
        var near = pricing(road("40")).estimate(booking("Colombo","Gampaha",DeliveryType.STANDARD));
        var far = pricing(road("390")).estimate(booking("Colombo","Jaffna",DeliveryType.STANDARD));
        assertTrue(far.totalFee().compareTo(near.totalFee()) > 0);
    }

    @Test void asksGoogleWithFullSriLankanAddresses() {
        List<String> asked = new ArrayList<>();
        pricing((from, to) -> { asked.add(from); asked.add(to); return Optional.empty(); })
                .estimate(booking("Colombo","Kandy",DeliveryType.STANDARD));
        assertEquals(List.of("Main Road, Colombo, Colombo District, 00100, Sri Lanka",
                "Lake Road, Kandy, Kandy District, 20000, Sri Lanka"), asked);
    }

    @Test void withoutADistanceTheDistrictBaseApplies() {
        var estimate=pricing.estimate(booking("Colombo","Kandy",DeliveryType.EXPRESS));
        assertEquals("LKR",estimate.currency());
        assertNull(estimate.distanceKm());
        assertEquals("Lake Road, Kandy, Kandy District, 20000, Sri Lanka", estimate.routeTo());
        assertEquals(0,new BigDecimal("650.00").compareTo(estimate.baseFee()));
        assertEquals(0,BigDecimal.ZERO.compareTo(estimate.distanceCharge()));
        assertEquals(0,new BigDecimal("180.00").compareTo(estimate.weightCharge()));
        assertEquals(0,new BigDecimal("350.00").compareTo(estimate.serviceCharge()));
        assertEquals(0,new BigDecimal("1180.00").compareTo(estimate.totalFee()));
        assertEquals("Standard SwiftDrop rates: district base + additional weight + service surcharge.", estimate.explanation());
    }

    @Test void multiParcelWeightIsIncludedInQuote() {
        var first = new ParcelRequest("Documents",new BigDecimal("1.00"),new BigDecimal("20"),
                new BigDecimal("15"),new BigDecimal("5"),null);
        var second = new ParcelRequest("Books",new BigDecimal("2.00"),new BigDecimal("30"),
                new BigDecimal("20"),new BigDecimal("10"),null);
        var pickup = new DeliveryAddressRequest("Sender","0771234567","Main Road","Kandy","Kandy",null);
        var dropoff = new DeliveryAddressRequest("Recipient","0711234567","Temple Road","Kandy","Kandy",null);
        var request = new CreateDeliveryRequest(DeliveryType.STANDARD,pickup,dropoff,List.of(first,second),null);
        assertEquals(0,new BigDecimal("590.00").compareTo(pricing.estimate(request).totalFee()));
    }

    @Test void sameDistrictStandardIsCheaperWithoutADistance() {
        var estimate=pricing.estimate(booking("Kandy","kandy",DeliveryType.STANDARD));
        assertEquals(0,new BigDecimal("530.00").compareTo(estimate.totalFee()));
    }
}
