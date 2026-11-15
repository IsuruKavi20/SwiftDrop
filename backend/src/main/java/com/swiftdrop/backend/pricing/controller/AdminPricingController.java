package com.swiftdrop.backend.pricing.controller;

import com.swiftdrop.backend.pricing.dto.PricingRatesRequest;
import com.swiftdrop.backend.pricing.dto.PublishedPricingResponse;
import com.swiftdrop.backend.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The admin's Pricing page: read the current tariff and publish a new version. ADMIN only. */
@RestController
@RequestMapping("/api/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
class AdminPricingController {

    private final PricingService pricing;

    AdminPricingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @GetMapping
    PublishedPricingResponse published() {
        return pricing.published();
    }

    /** New quotes use these rates at once; parcels already booked keep their fee. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PublishedPricingResponse publish(@Valid @RequestBody PricingRatesRequest request, @AuthenticationPrincipal Jwt user) {
        return pricing.publish(request, user);
    }
}
