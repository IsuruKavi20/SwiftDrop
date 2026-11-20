package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.customer.dto.FeeEstimateResponse;
import com.swiftdrop.backend.pricing.DeliveryPricing;
import org.springframework.stereotype.Service;

/** Adapts Customer requests and responses to the shared distance-based pricing calculation. */
@Service
public class CustomerPricingService {
    private final DeliveryPricing pricing;

    public CustomerPricingService(DeliveryPricing pricing) {
        this.pricing = pricing;
    }

    public FeeEstimateResponse estimate(CreateDeliveryRequest request) {
        var quote = pricing.estimate(address(request.pickup()), address(request.destination()),
                request.deliveryType().name(), request.parcels().stream().map(parcel -> parcel.weightKg()).toList());
        return new FeeEstimateResponse(quote.currency(), quote.distanceKm(), quote.routeFrom(), quote.routeTo(),
                quote.baseFee(), quote.distanceCharge(), quote.weightCharge(), quote.serviceCharge(),
                quote.totalFee(), quote.explanation());
    }

    private static DeliveryPricing.Address address(DeliveryAddressRequest request) {
        return new DeliveryPricing.Address(request.street(), request.city(), request.district(), request.postalCode());
    }
}
