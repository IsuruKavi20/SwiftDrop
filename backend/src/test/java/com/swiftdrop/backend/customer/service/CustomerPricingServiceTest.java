package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.*;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Fee is displayed before booking and saved unchanged on confirmation. */
class CustomerPricingServiceTest {
    private final CustomerPricingService pricing = new CustomerPricingService();
    private CreateDeliveryRequest booking(String origin, String destination, DeliveryType service) {
        var pickup=new DeliveryAddressRequest("Sender","0771234567","Main Road","Colombo",origin,"00100");
        var dropoff=new DeliveryAddressRequest("Recipient","0711234567","Lake Road","Kandy",destination,"20000");
        var parcel=new ParcelRequest("Books",new BigDecimal("2.50"),new BigDecimal("30"),
                new BigDecimal("20"),new BigDecimal("10"),null);
        return new CreateDeliveryRequest(service,pickup,dropoff,List.of(parcel),null);
    }
    @Test void producesTransparentCrossDistrictEstimate() {
        var estimate=pricing.estimate(booking("Colombo","Kandy",DeliveryType.EXPRESS));
        assertEquals("LKR",estimate.currency());
        assertEquals(0,new BigDecimal("650.00").compareTo(estimate.baseFee()));
        assertEquals(0,new BigDecimal("180.00").compareTo(estimate.weightCharge()));
        assertEquals(0,new BigDecimal("350.00").compareTo(estimate.serviceCharge()));
        assertEquals(0,new BigDecimal("1180.00").compareTo(estimate.totalFee()));
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
    @Test void sameDistrictStandardIsCheaper() {
        var estimate=pricing.estimate(booking("Kandy","kandy",DeliveryType.STANDARD));
        assertEquals(0,new BigDecimal("530.00").compareTo(estimate.totalFee()));
    }
}
