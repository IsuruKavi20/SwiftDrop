package com.swiftdrop.backend.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiChatResponse(
        String reply,
        String conversationId,
        List<String> toolsUsed,
        Map<String, Object> metadata,
        Instant timestamp
) {
    public static AiChatResponse of(String reply, String conversationId, List<String> toolsUsed, Map<String, Object> metadata) {
        return new AiChatResponse(reply, conversationId, toolsUsed != null ? toolsUsed : List.of(),
                metadata != null ? metadata : Map.of(), Instant.now());
    }
}

