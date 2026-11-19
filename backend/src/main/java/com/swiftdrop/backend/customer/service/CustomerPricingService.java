package com.swiftdrop.backend.customer.service;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.FeeEstimateResponse;
import com.swiftdrop.backend.pricing.DeliveryPricing;
import org.springframework.stereotype.Service;

/** Sri Lankan delivery quotes with the tariff an admin published (pricing module), or the server's settings while
 * none is published. All amounts are server-owned. The customer cannot alter a quote by changing frontend values,
 * and a booking stores its fee, so a later tariff never reprices it.
 */
@Service
public class CustomerPricingService {
    private final DeliveryPricing pricing;

    public CustomerPricingService(DeliveryPricing pricing) {
        this.pricing = pricing;
    }

    public FeeEstimateResponse estimate(CreateDeliveryRequest request) {
        var quote = pricing.estimate(request.pickup().district(), request.destination().district(),
                request.deliveryType().name(), request.parcels().stream().map(p -> p.weightKg()).toList());
        return new FeeEstimateResponse(quote.currency(), quote.baseFee(), quote.weightCharge(),
                quote.serviceCharge(), quote.totalFee(), quote.explanation());
    }
}
