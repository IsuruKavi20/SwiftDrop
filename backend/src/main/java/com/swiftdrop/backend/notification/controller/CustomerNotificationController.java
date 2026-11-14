package com.swiftdrop.backend.notification.controller;

import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.notification.dto.NotificationResponse;
import com.swiftdrop.backend.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/customers/me/notifications", "/api/v1/customer/notifications"})
@PreAuthorize("hasAuthority('parcel:read-own')")
public class CustomerNotificationController {

    private final NotificationService notificationService;

    public CustomerNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Paginated notification history for the authenticated customer.
     * GET /api/customers/me/notifications?page=0&size=10
     */
    @GetMapping
    public PageResponse<NotificationResponse> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt user) {
        UUID authUserId = UUID.fromString(user.getSubject());
        String email = user.getClaimAsString("email");
        return notificationService.getMyNotifications(authUserId, email, page, size);
    }

    /**
     * Unread/recent notification count for the notification bell badge.
     * GET /api/customers/me/notifications/count
     */
    @GetMapping("/count")
    public Map<String, Long> getNotificationCount(@AuthenticationPrincipal Jwt user) {
        UUID authUserId = UUID.fromString(user.getSubject());
        String email = user.getClaimAsString("email");
        long count = notificationService.getNotificationCount(authUserId, email);
        return Map.of("count", count);
    }

    /**
     * Dev/test helper: generate a test notification for UI verification.
     * POST /api/customers/me/notifications/test-trigger
     */
    @PostMapping("/test-trigger")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse triggerTest(
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt user) {
        UUID authUserId = UUID.fromString(user.getSubject());
        String email = user.getClaimAsString("email");
        return notificationService.triggerTestNotification(authUserId, email, trackingNumber, status);
    }
}

