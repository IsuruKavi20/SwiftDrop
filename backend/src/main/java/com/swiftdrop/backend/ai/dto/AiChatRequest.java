package com.swiftdrop.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AiChatRequest(
        @NotBlank(message = "Message must not be blank")
        String message,
        String conversationId,
        List<AiChatMessage> history
) {
    public AiChatRequest {
        if (history == null) {
            history = List.of();
        }
    }
}

