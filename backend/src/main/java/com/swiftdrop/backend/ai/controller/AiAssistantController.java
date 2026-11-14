package com.swiftdrop.backend.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.ai.dto.*;
import com.swiftdrop.backend.ai.service.AiAssistantService;
import com.swiftdrop.backend.ai.tool.DeliveryAiTools;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/ai", "/api/ai", "/api/public/ai"})
public class AiAssistantController {

    private final AiAssistantService aiService;
    private final DeliveryAiTools deliveryTools;
    private final ObjectMapper objectMapper;

    public AiAssistantController(AiAssistantService aiService, DeliveryAiTools deliveryTools, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.deliveryTools = deliveryTools;
        this.objectMapper = objectMapper;
    }

    /**
     * Primary chat endpoint for the frontend AI Assistant widget.
     * Works for both public guests (rate checks, tracking queries) and logged-in customers.
     */
    @PostMapping("/chat")
    public AiChatResponse chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal Jwt user
    ) {
        UUID authUserId = null;
        if (user != null && user.getSubject() != null) {
            try {
                authUserId = UUID.fromString(user.getSubject());
            } catch (IllegalArgumentException ignored) {}
        }
        return aiService.processMessage(request, authUserId);
    }

    /**
     * Model Context Protocol (MCP) Tools discovery endpoint.
     * Returns the tools schema registered with SwiftDrop.
     */
    @GetMapping("/mcp/tools")
    public List<McpToolDefinition> listMcpTools() {
        return deliveryTools.getMcpToolDefinitions();
    }

    /**
     * Model Context Protocol (MCP) Tool execution endpoint.
     * Allows MCP clients to invoke backend delivery tools directly.
     */
    @PostMapping("/mcp/tools/call")
    public McpToolCallResponse callMcpTool(
            @Valid @RequestBody McpToolCallRequest request,
            @AuthenticationPrincipal Jwt user
    ) {
        UUID authUserId = null;
        if (user != null && user.getSubject() != null) {
            try {
                authUserId = UUID.fromString(user.getSubject());
            } catch (IllegalArgumentException ignored) {}
        }

        try {
            Map<String, Object> result = deliveryTools.execute(request.name(), request.arguments(), authUserId);
            String jsonText = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return McpToolCallResponse.success(jsonText);
        } catch (Exception ex) {
            return McpToolCallResponse.error(ex.getMessage());
        }
    }
}

