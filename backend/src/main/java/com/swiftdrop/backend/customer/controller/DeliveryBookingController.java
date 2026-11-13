package com.swiftdrop.backend.customer.controller;

import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.DeliveryDetailsResponse;
import com.swiftdrop.backend.customer.service.DeliveryBookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Delivery booking: create a booking (PENDING), review it with GET /deliveries/{id}, then confirm it. */
@RestController
@RequestMapping("/api/customers/me/deliveries")
@PreAuthorize("hasAuthority('parcel:create')")
class DeliveryBookingController {

    private final DeliveryBookingService booking;

    DeliveryBookingController(DeliveryBookingService booking) {
        this.booking = booking;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DeliveryDetailsResponse createDelivery(@Valid @RequestBody CreateDeliveryRequest request, @AuthenticationPrincipal Jwt user) {
        return booking.createDelivery(CurrentUser.id(user), request);
    }

    @PostMapping("/{deliveryId}/confirm")
    DeliveryDetailsResponse confirmDelivery(@PathVariable Long deliveryId, @AuthenticationPrincipal Jwt user) {
        String email = user != null ? user.getClaimAsString("email") : null;
        return booking.confirmDelivery(CurrentUser.id(user), deliveryId, email);
    }
}
