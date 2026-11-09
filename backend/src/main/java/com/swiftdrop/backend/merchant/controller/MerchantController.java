package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.merchant.dto.MerchantResponse;
import com.swiftdrop.backend.merchant.dto.MerchantUpdateRequest;
import com.swiftdrop.backend.merchant.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The logged-in merchant's own business profile. For people only: merchant software (client-credentials tokens,
 * whose "sub" is a client id) gets 403 here; it will use the parcel APIs instead.
 */
@RestController
@RequestMapping("/api/merchants/me")
@PreAuthorize("hasAuthority('merchant:api-access')")
public class MerchantController {

    private final MerchantService merchantService;
    private final UserDirectory userDirectory;

    public MerchantController(MerchantService merchantService, UserDirectory userDirectory) {
        this.merchantService = merchantService;
        this.userDirectory = userDirectory;
    }

    /** First visit: an empty profile is created and returned with complete = false. */
    @GetMapping
    public MerchantResponse getProfile(@AuthenticationPrincipal Jwt token) {
        return MerchantResponse.from(merchantService.profileOf(owner(token)));
    }

    @PutMapping
    public MerchantResponse updateProfile(@Valid @RequestBody MerchantUpdateRequest request,
                                          @AuthenticationPrincipal Jwt token) {
        return MerchantResponse.from(merchantService.updateProfile(owner(token), request));
    }

    private AppUser owner(Jwt token) {
        if (!isPerson(token)) {
            throw new AccessDeniedException("The merchant profile is for people, not merchant software");
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
