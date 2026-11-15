package com.swiftdrop.backend.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.ai.dto.AiChatRequest;
import com.swiftdrop.backend.ai.dto.AiChatResponse;
import com.swiftdrop.backend.ai.dto.McpToolCallRequest;
import com.swiftdrop.backend.ai.dto.McpToolCallResponse;
import com.swiftdrop.backend.ai.dto.McpToolDefinition;
import com.swiftdrop.backend.ai.security.AiUsageLimiter;
import com.swiftdrop.backend.ai.security.AiUsageLimiter.Refusal;
import com.swiftdrop.backend.ai.service.AiAssistantService;
import com.swiftdrop.backend.ai.tool.DeliveryAiTools;
import com.swiftdrop.backend.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The AI delivery assistant.
 * <ul>
 *   <li>{@code POST /api/public/ai/chat}: open to guests (prices, policies, tracking status); a logged-in customer's
 *       token still counts, so "my deliveries" works for them. Rate-limited ({@link AiUsageLimiter}).</li>
 *   <li>{@code GET /api/public/ai/mcp/tools}: the list of tools (names and descriptions only, no data).</li>
 *   <li>{@code POST /api/ai/mcp/tools/call}: runs a tool directly, without the AI: logged-in customers only, and
 *       rate-limited too.</li>
 * </ul>
 */
@RestController
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final AiAssistantService aiService;
    private final DeliveryAiTools deliveryTools;
    private final ObjectMapper objectMapper;
    private final AiUsageLimiter limiter;

    public AiAssistantController(AiAssistantService aiService, DeliveryAiTools deliveryTools, ObjectMapper objectMapper,
                                 AiUsageLimiter limiter) {
        this.aiService = aiService;
        this.deliveryTools = deliveryTools;
        this.objectMapper = objectMapper;
        this.limiter = limiter;
    }

    /** The chat widget. Guests and logged-in customers; the AI never decides who the caller is (the token does). */
    @PostMapping("/api/public/ai/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request, @AuthenticationPrincipal Jwt user,
                               HttpServletRequest http) {
        UUID authUserId = userId(user);
        checkLimit(authUserId, http);
        return aiService.processMessage(request, authUserId);
    }

    /** Model Context Protocol (MCP) discovery: the tools the AI can use (schema only). */
    @GetMapping("/api/public/ai/mcp/tools")
    public List<McpToolDefinition> listMcpTools() {
        return deliveryTools.getMcpToolDefinitions();
    }

    /** MCP tool execution without the AI. Logged-in customers only. */
    @PostMapping("/api/ai/mcp/tools/call")
    @PreAuthorize("hasAuthority('parcel:read-own')")
    public McpToolCallResponse callMcpTool(@Valid @RequestBody McpToolCallRequest request,
                                           @AuthenticationPrincipal Jwt user, HttpServletRequest http) {
        UUID authUserId = userId(user);
        checkLimit(authUserId, http);
        try {
            Map<String, Object> result = deliveryTools.execute(request.name(), request.arguments(), authUserId);
            return McpToolCallResponse.success(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (Exception ex) {
            // Details stay in our log; the caller gets a plain message (no class names or SQL)
            log.warn("MCP tool {} failed: {}", request.name(), ex.getMessage());
            return McpToolCallResponse.error("The tool could not run. Check the tool name and arguments.");
        }
    }

    @ExceptionHandler(AiLimitException.class)
    ResponseEntity<ProblemDetail> tooMany(AiLimitException exception) {
        String detail = switch (exception.refusal) {
            case TOO_FAST -> "You've sent a lot of messages. Please wait a minute and try again.";
            case DAILY_LIMIT -> "You've reached today's limit for the assistant. Please try again tomorrow.";
            case SITE_BUSY -> "The assistant is very busy today. Please try again tomorrow, or use Track parcel.";
        };
        String retryAfterSeconds = exception.refusal == Refusal.TOO_FAST ? "60" : "3600";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds)
                .body(Problems.of(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", detail));
    }

    // Logged-in customers are counted by account, guests by IP address (the real one: see
    // server.forward-headers-strategy in application.properties)
    private void checkLimit(UUID authUserId, HttpServletRequest http) {
        String key = authUserId != null ? "user:" + authUserId : "ip:" + http.getRemoteAddr();
        Refusal refusal = limiter.tryUse(key, authUserId != null);
        if (refusal != null) {
            throw new AiLimitException(refusal);
        }
    }

    private static UUID userId(Jwt user) {
        if (user == null || user.getSubject() == null) {
            return null;
        }
        try {
            return UUID.fromString(user.getSubject());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    static final class AiLimitException extends RuntimeException {
        private final Refusal refusal;

        AiLimitException(Refusal refusal) {
            super("AI usage limit: " + refusal, null, false, false);
            this.refusal = refusal;
        }
    }
}
