package com.swiftdrop.backend.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the tracking link sent to customers and receivers by email/SMS.
 * Prefers the random tracking-link code ({@code /track/link/<code>}): it can't be guessed, unlike a short tracking
 * number. The website address comes from {@code swiftdrop.frontend-url} (production: https://app.getswiftdrop.me).
 */
@Component
public class TrackingLinks {

    private final String frontendUrl;

    public TrackingLinks(@Value("${swiftdrop.frontend-url:http://localhost:5173}") String frontendUrl) {
        if (!frontendUrl.startsWith("https://") && !frontendUrl.startsWith("http://localhost")) {
            throw new IllegalArgumentException("swiftdrop.frontend-url must be an https address (or http://localhost)");
        }
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public String forDelivery(String publicTrackingToken, String trackingNumber) {
        if (publicTrackingToken != null && !publicTrackingToken.isBlank()) {
            return frontendUrl + "/track/link/" + encode(publicTrackingToken);
        }
        return frontendUrl + "/track/" + encode(trackingNumber);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
