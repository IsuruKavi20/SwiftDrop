package com.swiftdrop.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftdrop.ai.gemini")
public record AiProperties(
        String apiKey,
        String model
) {
    public AiProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-3.5-flash-lite";
        }
    }
}

