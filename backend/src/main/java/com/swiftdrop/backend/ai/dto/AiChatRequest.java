package com.swiftdrop.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One chat message plus the earlier conversation. Size limits keep each Gemini call small (cost) and stop a caller
 * from sending a huge fake history.
 */
public record AiChatRequest(
        @NotBlank(message = "Message must not be blank")
        @Size(max = MAX_MESSAGE_CHARS, message = "Message must be at most 500 characters")
        String message,
        @Size(max = 64)
        String conversationId,
        List<AiChatMessage> history
) {
    public static final int MAX_MESSAGE_CHARS = 500;
    static final int MAX_HISTORY_MESSAGES = 10;
    static final int MAX_HISTORY_CHARS = 1000;

    public AiChatRequest {
        history = cleanHistory(history);
    }

    /**
     * Keeps only the last 10 messages, each cut to 1000 characters, and only the roles "user" and "model": the
     * browser sends the history, so it must not be able to pose as the system or as a tool result.
     */
    private static List<AiChatMessage> cleanHistory(List<AiChatMessage> history) {
        if (history == null) {
            return List.of();
        }
        List<AiChatMessage> usable = history.stream()
                .filter(m -> m != null && m.content() != null && ("user".equals(m.role()) || "model".equals(m.role())))
                .map(m -> new AiChatMessage(m.role(), m.content().length() > MAX_HISTORY_CHARS
                        ? m.content().substring(0, MAX_HISTORY_CHARS) : m.content()))
                .toList();
        return usable.size() > MAX_HISTORY_MESSAGES
                ? List.copyOf(usable.subList(usable.size() - MAX_HISTORY_MESSAGES, usable.size()))
                : usable;
    }
}
