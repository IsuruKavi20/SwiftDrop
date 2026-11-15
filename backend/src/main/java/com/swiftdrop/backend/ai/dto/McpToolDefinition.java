package com.swiftdrop.backend.ai.dto;

import java.util.Map;

public record McpToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
}

