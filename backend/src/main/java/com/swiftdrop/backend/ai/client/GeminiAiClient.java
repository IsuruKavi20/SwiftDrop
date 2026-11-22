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

            // The key goes in a header: addresses end up in logs and proxies, headers do not
            String url = GEMINI_API_BASE + properties.model() + ":generateContent";
            log.info("Calling Gemini API model: {}", properties.model());

            String responseJson = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", properties.apiKey())
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

            // The key goes in a header: addresses end up in logs and proxies, headers do not
            String url = GEMINI_API_BASE + properties.model() + ":generateContent";
            String responseJson = executeWithRetry(url, requestBody);

            JsonNode root = objectMapper.readTree(responseJson);
            GeminiTurnResult result = parseCandidate(root);
            return result.text() != null ? result.text() : formatToolFallback(toolCall.name(), toolResult);

        } catch (Exception ex) {
            log.error("Failed to submit tool response to Gemini: {}", ex.getMessage(), ex);
            // Fallback: format tool result gracefully in markdown if Gemini roundtrip fails
            return formatToolFallback(toolCall.name(), toolResult);
        }
    }

    private String executeWithRetry(String url, Map<String, Object> requestBody) {
        int maxAttempts = 2;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-goog-api-key", properties.apiKey())
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < maxAttempts) {
                    log.warn("Gemini API call attempt {} failed ({}), retrying in 1s...", attempt, ex.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastException instanceof RuntimeException re) throw re;
        throw new RuntimeException("Gemini call failed after retries", lastException);
    }

    /**
     * Graceful fallback formatter for tool executions when Gemini is unavailable or errors out.
     */
    public static String formatToolFallback(String toolName, Map<String, Object> toolResult) {
        if (toolResult == null || toolResult.isEmpty()) {
            return "No information was returned for this request.";
        }

        if ("calculateDeliveryCost".equals(toolName)) {
            return formatPricingFallback(toolResult);
        } else if ("trackParcel".equals(toolName)) {
            return formatTrackingFallback(toolResult);
        } else if ("listMyDeliveries".equals(toolName)) {
            return formatDeliveriesFallback(toolResult);
        } else if ("getDeliveryPolicyInfo".equals(toolName)) {
            return formatPolicyFallback(toolResult);
        }

        // Generic fallback
        if (toolResult.containsKey("message")) {
            return String.valueOf(toolResult.get("message"));
        }
        if (toolResult.containsKey("summary")) {
            return String.valueOf(toolResult.get("summary"));
        }

        StringBuilder sb = new StringBuilder("Here is the requested delivery information:\n\n");
        toolResult.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()) {
                sb.append("- **").append(k).append(":** ").append(v).append("\n");
            }
        });
        return sb.toString().trim();
    }

    private static String formatPricingFallback(Map<String, Object> d) {
        String currency = Objects.toString(d.getOrDefault("currency", "LKR"), "LKR");
        String weight = Objects.toString(d.getOrDefault("weightKg", "1.0"), "1.0");
        String type = Objects.toString(d.getOrDefault("deliveryType", "STANDARD"), "STANDARD");
        String timeline = Objects.toString(d.getOrDefault("estimatedTimeline", "2-3 business days"), "2-3 business days");

        String from = d.containsKey("pickupCity") && !d.get("pickupCity").toString().isBlank()
                ? d.get("pickupCity") + " (" + d.get("pickupDistrict") + ")"
                : Objects.toString(d.get("pickupDistrict"), "Colombo");
        String to = d.containsKey("destinationCity") && !d.get("destinationCity").toString().isBlank()
                ? d.get("destinationCity") + " (" + d.get("destinationDistrict") + ")"
                : Objects.toString(d.get("destinationDistrict"), "Colombo");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("The estimated delivery cost from **%s** to **%s** for a **%s kg** parcel is **%s %s** using **%s** delivery.\n\n",
                from, to, weight, currency, formatNumber(d.get("totalEstimatedFee")), type));

        sb.append("### Details:\n");
        sb.append(String.format("- **Estimated Timeline:** %s\n", timeline));
        if (d.containsKey("roadDistanceKm")) {
            sb.append(String.format("- **Road Distance:** %s km\n", d.get("roadDistanceKm")));
        }
        if (d.containsKey("baseFee")) {
            sb.append(String.format("- **Base Fee:** %s %s\n", currency, formatNumber(d.get("baseFee"))));
        }
        if (d.containsKey("distanceCharge") && hasPositiveAmount(d.get("distanceCharge"))) {
            sb.append(String.format("- **Distance Charge:** %s %s\n", currency, formatNumber(d.get("distanceCharge"))));
        }
        if (d.containsKey("extraWeightFee") && hasPositiveAmount(d.get("extraWeightFee"))) {
            sb.append(String.format("- **Extra Weight Fee:** %s %s\n", currency, formatNumber(d.get("extraWeightFee"))));
        }
        if (d.containsKey("serviceSurcharge") && hasPositiveAmount(d.get("serviceSurcharge"))) {
            sb.append(String.format("- **Service Surcharge:** %s %s\n", currency, formatNumber(d.get("serviceSurcharge"))));
        }
        sb.append(String.format("- **Total Estimated Cost:** **%s %s**\n\n", currency, formatNumber(d.get("totalEstimatedFee"))));

        if (d.containsKey("pricingExplanation")) {
            sb.append("*").append(d.get("pricingExplanation")).append("*\n\n");
        }
        sb.append("Let me know if you would like to arrange this pickup or check other options!");
        return sb.toString();
    }

    private static String formatTrackingFallback(Map<String, Object> d) {
        if (Boolean.FALSE.equals(d.get("found"))) {
            return Objects.toString(d.get("message"), "No parcel found matching that tracking identifier.");
        }
        String tn = Objects.toString(d.get("trackingNumber"), "N/A");
        String status = Objects.toString(d.get("status"), "UNKNOWN");
        String type = Objects.toString(d.get("deliveryType"), "STANDARD");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### Tracking Details: %s\n", tn));
        sb.append(String.format("- **Status:** %s\n", status));
        sb.append(String.format("- **Delivery Tier:** %s\n", type));
        if (d.containsKey("pickupCity") || d.containsKey("pickupDistrict")) {
            sb.append(String.format("- **Pickup:** %s, %s\n", d.getOrDefault("pickupCity", ""), d.getOrDefault("pickupDistrict", "")));
        }
        if (d.containsKey("destinationCity") || d.containsKey("destinationDistrict")) {
            sb.append(String.format("- **Destination:** %s, %s\n", d.getOrDefault("destinationCity", ""), d.getOrDefault("destinationDistrict", "")));
        }
        if (d.containsKey("estimatedDeliveryDate")) {
            sb.append(String.format("- **Estimated Delivery:** %s\n", d.get("estimatedDeliveryDate")));
        }
        if (d.containsKey("trackingUrl")) {
            sb.append(String.format("\nTrack live at: %s\n", d.get("trackingUrl")));
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static String formatDeliveriesFallback(Map<String, Object> d) {
        if (Boolean.FALSE.equals(d.get("authenticated"))) {
            return Objects.toString(d.get("message"), "Please log in to view your recent deliveries.");
        }
        List<Map<String, Object>> deliveries = (List<Map<String, Object>>) d.get("deliveries");
        if (deliveries == null || deliveries.isEmpty()) {
            return "You do not have any recent deliveries on your account.";
        }
        StringBuilder sb = new StringBuilder("### Your Recent Deliveries:\n\n");
        for (Map<String, Object> del : deliveries) {
            String tn = Objects.toString(del.get("trackingNumber"), "");
            String st = Objects.toString(del.get("status"), "");
            String city = Objects.toString(del.get("destinationCity"), "N/A");
            String tier = Objects.toString(del.get("deliveryType"), "STANDARD");
            sb.append(String.format("- **%s** (%s) ➔ %s — *%s*\n", tn, tier, city, st));
        }
        return sb.toString().trim();
    }

    private static String formatPolicyFallback(Map<String, Object> d) {
        String topic = Objects.toString(d.get("topic"), "Information");
        String policy = Objects.toString(d.get("policy"), "");
        return String.format("### %s\n\n%s", topic, policy);
    }

    private static boolean hasPositiveAmount(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue() > 0;
        }
        try {
            return Double.parseDouble(Objects.toString(val, "0")) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String formatNumber(Object val) {
        if (val == null) return "0.00";
        try {
            double d = Double.parseDouble(val.toString());
            return String.format(Locale.US, "%,.2f", d);
        } catch (Exception e) {
            return val.toString();
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

