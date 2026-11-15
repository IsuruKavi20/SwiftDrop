package com.swiftdrop.backend.ai.service;

import com.swiftdrop.backend.ai.client.GeminiAiClient;
import com.swiftdrop.backend.ai.dto.AiChatRequest;
import com.swiftdrop.backend.ai.dto.AiChatResponse;
import com.swiftdrop.backend.ai.tool.DeliveryAiTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private static final String SYSTEM_INSTRUCTION = """
            You are SwiftDrop AI, the official delivery assistant for SwiftDrop, Sri Lanka's leading parcel delivery platform.
            Your job is to assist customers with tracking their shipments, calculating delivery rates, explaining services, and answering questions.
            
            Guidelines:
            - When a user asks about shipping costs (e.g. "How much to send 2 kg to Kandy?"), call `calculateDeliveryCost`.
            - When a user asks about a parcel's location or status (e.g. "Where is SD-123456?"), call `trackParcel`.
            - When a logged-in user asks about their recent orders (e.g. "What are my deliveries?"), call `listMyDeliveries`.
            - When a user asks about order cancellation, prohibited goods, delivery speeds, or customer support, call `getDeliveryPolicyInfo`.
            - Never invent tracking events or random prices: always use the data returned by your tools.
            - All monetary amounts are in Sri Lankan Rupees (LKR).
            - Keep answers courteous, clear, and formatted with markdown.
            """;

    private final GeminiAiClient geminiClient;
    private final DeliveryAiTools deliveryTools;

    public AiAssistantService(GeminiAiClient geminiClient, DeliveryAiTools deliveryTools) {
        this.geminiClient = geminiClient;
        this.deliveryTools = deliveryTools;
    }

    /**
     * Processes customer chat message with LLM function calling.
     */
    public AiChatResponse processMessage(AiChatRequest request, UUID authUserId) {
        String convId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        List<Map<String, Object>> tools = deliveryTools.getGeminiToolDeclarations();
        List<String> toolsUsed = new ArrayList<>();
        Map<String, Object> metadata = new LinkedHashMap<>();

        // 1. First turn: Send conversation history + prompt to Gemini
        GeminiAiClient.GeminiTurnResult turn = geminiClient.generateWithTools(
                request.history(),
                request.message(),
                tools,
                SYSTEM_INSTRUCTION
        );

        // 2. If Gemini wants to execute a tool (e.g. track parcel or calculate fee)
        if (turn.hasToolCall()) {
            GeminiAiClient.ToolCall call = turn.toolCall();
            toolsUsed.add(call.name());
            log.info("Gemini requested tool: {} with args: {}", call.name(), call.arguments());

            // Execute backend tool
            Map<String, Object> toolResult = deliveryTools.execute(call.name(), call.arguments(), authUserId);
            metadata.put("toolName", call.name());
            metadata.put("toolArgs", call.arguments());
            metadata.put("toolResult", toolResult);

            // 3. Second turn: Feed function response back to Gemini to synthesize human response
            String finalReply = geminiClient.sendToolResponse(
                    request.history(),
                    request.message(),
                    call,
                    toolResult,
                    tools,
                    SYSTEM_INSTRUCTION
            );

            return AiChatResponse.of(finalReply, convId, toolsUsed, metadata);
        }

        // Direct answer without tools
        return AiChatResponse.of(turn.text(), convId, toolsUsed, metadata);
    }
}

