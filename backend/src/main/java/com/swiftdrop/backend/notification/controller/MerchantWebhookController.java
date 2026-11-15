package com.swiftdrop.backend.notification.controller;

import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.notification.dto.CreateWebhookRequest;
import com.swiftdrop.backend.notification.dto.UpdateWebhookRequest;
import com.swiftdrop.backend.notification.dto.WebhookConfigResponse;
import com.swiftdrop.backend.notification.dto.WebhookDeliveryLogResponse;
import com.swiftdrop.backend.notification.service.MerchantWebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchants/me/webhooks")
@PreAuthorize("hasAuthority('merchant:api-access')")
public class MerchantWebhookController {

    private final MerchantWebhookService webhookService;
    private final UserDirectory userDirectory;

    public MerchantWebhookController(MerchantWebhookService webhookService, UserDirectory userDirectory) {
        this.webhookService = webhookService;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    public List<WebhookConfigResponse> getWebhooks(@AuthenticationPrincipal Jwt token) {
        return webhookService.getWebhooksForOwner(owner(token));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookConfigResponse createWebhook(@Valid @RequestBody CreateWebhookRequest request,
                                               @AuthenticationPrincipal Jwt token) {
        return webhookService.createWebhook(owner(token), request);
    }

    @GetMapping("/{id}")
    public WebhookConfigResponse getWebhook(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt token) {
        return webhookService.getWebhookForOwner(owner(token), id);
    }

    @PutMapping("/{id}")
    public WebhookConfigResponse updateWebhook(@PathVariable Long id,
                                               @Valid @RequestBody UpdateWebhookRequest request,
                                               @AuthenticationPrincipal Jwt token) {
        return webhookService.updateWebhook(owner(token), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWebhook(@PathVariable Long id,
                              @AuthenticationPrincipal Jwt token) {
        webhookService.deleteWebhook(owner(token), id);
    }

    @GetMapping("/{id}/logs")
    public List<WebhookDeliveryLogResponse> getLogs(@PathVariable Long id,
                                                   @AuthenticationPrincipal Jwt token) {
        return webhookService.getLogsForWebhook(owner(token), id);
    }

    @GetMapping("/logs/recent")
    public List<WebhookDeliveryLogResponse> getRecentLogs(@AuthenticationPrincipal Jwt token) {
        return webhookService.getRecentLogsForOwner(owner(token));
    }

    @PostMapping("/{id}/test")
    public WebhookDeliveryLogResponse testWebhook(@PathVariable Long id,
                                                 @AuthenticationPrincipal Jwt token) {
        return webhookService.testWebhook(owner(token), id);
    }

    private AppUser owner(Jwt token) {
        if (!isPerson(token)) {
            throw new AccessDeniedException("The merchant webhook endpoints require a valid user account");
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
