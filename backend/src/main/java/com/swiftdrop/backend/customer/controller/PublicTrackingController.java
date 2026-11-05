package com.swiftdrop.backend.customer.controller;

import com.swiftdrop.backend.customer.dto.TrackingResponse;
import com.swiftdrop.backend.customer.service.PublicTrackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Tracking: no login (SecurityConfig permits GET /api/public/**). Answers contain no private data.
 * The frontend must not send an Authorization header here: an expired token would be rejected with 401.
 */
@RestController
@RequestMapping("/api/public/tracking")
class PublicTrackingController {

    private final PublicTrackingService tracking;

    PublicTrackingController(PublicTrackingService tracking) {
        this.tracking = tracking;
    }

    /** By the tracking number printed on the booking, e.g. GET /api/public/tracking/SD-7K3M9QW2XA */
    @GetMapping("/{trackingNumber}")
    TrackingResponse trackByTrackingNumber(@PathVariable String trackingNumber) {
        return tracking.trackByTrackingNumber(trackingNumber);
    }

    /** By the token in a shared tracking link, e.g. GET /api/public/tracking/link/3q2-x... */
    @GetMapping("/link/{token}")
    TrackingResponse trackByLink(@PathVariable String token) {
        return tracking.trackByPublicToken(token);
    }
}
