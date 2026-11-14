package com.swiftdrop.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.ai.config.AiProperties;
import com.swiftdrop.backend.ai.dto.AiChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
@EnableConfigurationProperties(AiProperties.class)
public class GeminiAiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(GEMINI_API_BASE)
                .build();
    }

    public record ToolCall(
            String name,
            Map<String, Object> arguments,
            String callId,
            String thoughtSignature,
            Map<String, Object> rawModelPart
    ) {}

    public record GeminiTurnResult(
            String text,
            ToolCall toolCall,
            JsonNode rawCandidate
    ) {
        public boolean hasToolCall() {
            return toolCall != null;
        }
    }

    /**
     * Sends conversation to Gemini with function declarations.
     */
    public GeminiTurnResult generateWithTools(
            List<AiChatMessage> history,
            String userPrompt,
            List<Map<String, Object>> tools,
            String systemInstruction
    ) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return new GeminiTurnResult("Gemini API key is not configured. Please set swiftdrop.ai.gemini.api-key in application-local.properties.", null, null);
        }

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();

            // System Instruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }

            // Contents array
            List<Map<String, Object>> contents = new ArrayList<>();
            if (history != null) {
                for (AiChatMessage msg : history) {
                    String role = "user".equalsIgnoreCase(msg.role()) ? "user" : "model";
                    contents.add(Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", msg.content()))
                    ));
                }
            }
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userPrompt))
            ));
            requestBody.put("contents", contents);

            // Tools array
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", List.of(
                        Map.of("functionDeclarations", tools)
                ));
            }

            String url = GEMINI_API_BASE + properties.model() + ":generateContent?key=" + properties.apiKey();
            log.info("Calling Gemini API model: {}", properties.model());

            String responseJson = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            return parseCandidate(root);

        } catch (Exception ex) {
            log.error("Gemini API call failed: {}", ex.getMessage(), ex);
            return new GeminiTurnResult("Sorry, I'm having trouble connecting to the SwiftDrop AI service right now. Please try again shortly.", null, null);
        }
    }

    /**
     * Follows up a tool call turn by sending the functionResponse back to Gemini for final natural text.
     */
    public String sendToolResponse(
            List<AiChatMessage> history,
            String userPrompt,
            ToolCall toolCall,
            Map<String, Object> toolResult,
            List<Map<String, Object>> tools,
            String systemInstruction
    ) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();

            if (systemInstruction != null && !systemInstruction.isBlank()) {
                requestBody.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }

            List<Map<String, Object>> contents = new ArrayList<>();
            if (history != null) {
                for (AiChatMessage msg : history) {
                    String role = "user".equalsIgnoreCase(msg.role()) ? "user" : "model";
                    contents.add(Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", msg.content()))
                    ));
                }
            }

            // User prompt
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userPrompt))
            ));

            // Model turn containing the functionCall (preserving raw part including thoughtSignature)
            contents.add(Map.of(
                    "role", "model",
                    "parts", List.of(toolCall.rawModelPart())
            ));

            // Function response from user
            Map<String, Object> funcRespPart = new LinkedHashMap<>();
            Map<String, Object> funcRespData = new LinkedHashMap<>();
            funcRespData.put("name", toolCall.name());
            funcRespData.put("response", toolResult);
            funcRespPart.put("functionResponse", funcRespData);

            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(funcRespPart)
            ));

            requestBody.put("contents", contents);

            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", List.of(
                        Map.of("functionDeclarations", tools)
                ));
            }

            String url = GEMINI_API_BASE + properties.model() + ":generateContent?key=" + properties.apiKey();
            String responseJson = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            GeminiTurnResult result = parseCandidate(root);
            return result.text() != null ? result.text() : "Here is your delivery update.";

        } catch (Exception ex) {
            log.error("Failed to submit tool response to Gemini: {}", ex.getMessage(), ex);
            // Fallback: format tool result nicely as text if Gemini roundtrip fails
            return "Result: " + toolResult.toString();
        }
    }

    private GeminiTurnResult parseCandidate(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return new GeminiTurnResult("No response generated by AI model.", null, null);
        }

        JsonNode first = candidates.get(0);
        JsonNode parts = first.path("content").path("parts");

        if (parts.isArray() && !parts.isEmpty()) {
            for (JsonNode part : parts) {
                if (part.hasNonNull("functionCall")) {
                    JsonNode call = part.get("functionCall");
                    String name = call.path("name").asText();
                    String callId = call.path("id").asText(null);
                    String thoughtSig = part.path("thoughtSignature").asText(null);

                    Map<String, Object> args = new HashMap<>();
                    if (call.hasNonNull("args")) {
                        call.get("args").fields().forEachRemaining(entry -> {
                            JsonNode val = entry.getValue();
                            if (val.isNumber()) args.put(entry.getKey(), val.numberValue());
                            else if (val.isBoolean()) args.put(entry.getKey(), val.asBoolean());
                            else args.put(entry.getKey(), val.asText());
                        });
                    }
                    Map<String, Object> partMap = objectMapper.convertValue(part, Map.class);
                    ToolCall toolCall = new ToolCall(name, args, callId, thoughtSig, partMap);
                    return new GeminiTurnResult(null, toolCall, first);
                } else if (part.hasNonNull("text")) {
                    return new GeminiTurnResult(part.get("text").asText(), null, first);
                }
            }
        }

        return new GeminiTurnResult("I could not generate an answer. Please ask in another way.", null, first);
    }
}

