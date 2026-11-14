package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDashboardResponse;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.dto.MerchantDeliverySummaryResponse;
import com.swiftdrop.backend.merchant.service.MerchantDeliveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API for merchant deliveries and dashboard metrics.
 * Accessible to authenticated merchants with the 'merchant:api-access' authority.
 */
@RestController
@RequestMapping("/api/merchants/me")
@PreAuthorize("hasAuthority('merchant:api-access')")
public class MerchantDeliveryController {

    private final MerchantDeliveryService deliveryService;
    private final UserDirectory userDirectory;

    public MerchantDeliveryController(MerchantDeliveryService deliveryService, UserDirectory userDirectory) {
        this.deliveryService = deliveryService;
        this.userDirectory = userDirectory;
    }

    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantDeliveryDetailResponse createDelivery(@Valid @RequestBody CreateMerchantDeliveryRequest request,
                                                         @AuthenticationPrincipal Jwt token) {
        return deliveryService.createDelivery(owner(token), request);
    }

    @GetMapping("/deliveries")
    public PageResponse<MerchantDeliverySummaryResponse> getDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt token) {
        return deliveryService.getDeliveries(owner(token), status, page, size);
    }

    @GetMapping("/deliveries/{id}")
    public MerchantDeliveryDetailResponse getDelivery(@PathVariable Long id,
                                                      @AuthenticationPrincipal Jwt token) {
        return deliveryService.getDeliveryDetail(owner(token), id);
    }

    @GetMapping("/dashboard")
    public MerchantDashboardResponse getDashboard(@AuthenticationPrincipal Jwt token) {
        return deliveryService.getDashboard(owner(token));
    }

    private AppUser owner(Jwt token) {
        if (!isPerson(token)) {
            throw new AccessDeniedException("The merchant endpoints require a valid user account");
        }
        return userDirectory.caller(token);
    }

    private static boolean isPerson(Jwt token) {
        try {
            UUID.fromString(token.getSubject());
            return true;
        } catch (IllegalArgumentException | NullPointerException notAPerson) {
            return false;
        }
    }
}
