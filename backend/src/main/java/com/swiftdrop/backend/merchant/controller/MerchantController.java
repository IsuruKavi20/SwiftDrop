
package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.merchant.dto.MerchantResponse;
import com.swiftdrop.backend.merchant.dto.MerchantUpdateRequest;
import com.swiftdrop.backend.merchant.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchants/me")
@PreAuthorize("hasAuthority('merchant:api-access')")
public class MerchantController  {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    private UUID currentUserId(Jwt user) {
        return UUID.fromString(user.getSubject());
    }

    @GetMapping
    public MerchantResponse getProfile(
            @AuthenticationPrincipal Jwt user
    ) {
        return MerchantResponse.from(
                merchantService.getMerchantProfile(currentUserId(user))
        );
    }

    @PutMapping
    public MerchantResponse updateProfile(
            @Valid @RequestBody MerchantUpdateRequest request,
            @AuthenticationPrincipal Jwt user
    ) {
        return MerchantResponse.from(
                merchantService.updateMerchantProfile(
                        currentUserId(user),
                        request
                )
        );
    }
}
