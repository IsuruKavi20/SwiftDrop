package com.swiftdrop.backend.ai.dto;

import java.util.List;
import java.util.Map;

public record McpToolCallResponse(
        List<Map<String, String>> content,
        boolean isError
) {
    public static McpToolCallResponse success(String text) {
        return new McpToolCallResponse(List.of(Map.of("type", "text", "text", text)), false);
    }

    public static McpToolCallResponse error(String message) {
        return new McpToolCallResponse(List.of(Map.of("type", "text", "text", "Error: " + message)), true);
    }
}

