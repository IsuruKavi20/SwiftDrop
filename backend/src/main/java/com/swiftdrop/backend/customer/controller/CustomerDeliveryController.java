package com.swiftdrop.backend.customer.controller;

import com.swiftdrop.backend.customer.dto.DashboardResponse;
import com.swiftdrop.backend.customer.dto.DeliveryDetailsResponse;
import com.swiftdrop.backend.customer.dto.DeliverySummaryResponse;
import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.customer.dto.TrackingResponse;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.service.CustomerDeliveryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery management and tracking for the logged-in customer: dashboard, My Deliveries, delivery details and
 * Track Parcel. Needs "parcel:read-own"; the services only ever return the caller's own deliveries.
 */
@RestController
@RequestMapping("/api/customers/me")
@PreAuthorize("hasAuthority('parcel:read-own')")
class CustomerDeliveryController {

    private final CustomerDeliveryService deliveries;

    CustomerDeliveryController(CustomerDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping("/dashboard")
    DashboardResponse getDashboard(@AuthenticationPrincipal Jwt user) {
        return deliveries.getDashboard(CurrentUser.id(user));
    }

    /** e.g. GET /api/customers/me/deliveries?status=IN_TRANSIT&search=kandy&page=0&size=10 */
    @GetMapping("/deliveries")
    PageResponse<DeliverySummaryResponse> getMyDeliveries(@RequestParam(required = false) DeliveryStatus status,
                                                          @RequestParam(required = false) String search,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @AuthenticationPrincipal Jwt user) {
        return deliveries.getMyDeliveries(CurrentUser.id(user), status, search, page, size);
    }

    @GetMapping("/deliveries/{deliveryId}")
    DeliveryDetailsResponse getDeliveryDetails(@PathVariable Long deliveryId, @AuthenticationPrincipal Jwt user) {
        return deliveries.getDeliveryDetails(CurrentUser.id(user), deliveryId);
    }

    @GetMapping("/tracking/{trackingNumber}")
    TrackingResponse trackMyDelivery(@PathVariable String trackingNumber, @AuthenticationPrincipal Jwt user) {
        return deliveries.trackMyDelivery(CurrentUser.id(user), trackingNumber);
    }
}
