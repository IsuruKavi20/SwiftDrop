package com.swiftdrop.backend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWebhookRequest(
        @NotBlank(message = "Endpoint URL is required")
        @Size(max = 500, message = "Endpoint URL cannot exceed 500 characters")
        String endpointUrl,

        @NotBlank(message = "Subscribed events are required")
        @Size(max = 255, message = "Subscribed events cannot exceed 255 characters")
        String subscribedEvents,

        @Size(max = 128, message = "Secret key cannot exceed 128 characters")
        String secretKey
) {}
