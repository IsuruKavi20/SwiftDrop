package com.swiftdrop.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record McpToolCallRequest(
        @NotBlank(message = "Tool name is required")
        String name,
        Map<String, Object> arguments
) {
    public McpToolCallRequest {
        if (arguments == null) {
            arguments = Map.of();
        }
    }
}

