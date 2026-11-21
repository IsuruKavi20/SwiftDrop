package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.security.MerchantMachineBookingAccess;
import com.swiftdrop.backend.merchant.service.MerchantIntegrationBookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant-integrations/deliveries")
public class MerchantIntegrationBookingController {
    private final MerchantMachineBookingAccess access;
    private final MerchantIntegrationBookingService bookings;

    public MerchantIntegrationBookingController(MerchantMachineBookingAccess access,
                                                MerchantIntegrationBookingService bookings) {
        this.access = access;
        this.bookings = bookings;
    }

    @PostMapping
    @PreAuthorize("@merchantMachineBookingAccess.isMachine(authentication.principal)")
    public ResponseEntity<MerchantDeliveryDetailResponse> book(@AuthenticationPrincipal Jwt token,
                                                               @RequestHeader("Idempotency-Key") String key,
                                                               @Valid @RequestBody CreateMerchantDeliveryRequest request) {
        var result = bookings.book(access.requireBooking(token), key, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.delivery());
    }
}
