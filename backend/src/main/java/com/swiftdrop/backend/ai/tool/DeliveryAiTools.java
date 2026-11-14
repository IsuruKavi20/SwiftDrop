package com.swiftdrop.backend.ai.tool;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.ai.dto.McpToolDefinition;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class DeliveryAiTools {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAiTools.class);

    private final EntityManager entityManager;

    @Value("${swiftdrop.customer.pricing.same-district-base:350.00}")
    private BigDecimal sameDistrictBase = new BigDecimal("350.00");

    @Value("${swiftdrop.customer.pricing.other-district-base:650.00}")
    private BigDecimal otherDistrictBase = new BigDecimal("650.00");

    @Value("${swiftdrop.customer.pricing.additional-kg:120.00}")
    private BigDecimal additionalKg = new BigDecimal("120.00");

    @Value("${swiftdrop.customer.pricing.express-surcharge:350.00}")
    private BigDecimal expressSurcharge = new BigDecimal("350.00");

    @Value("${swiftdrop.customer.pricing.same-day-surcharge:650.00}")
    private BigDecimal sameDaySurcharge = new BigDecimal("650.00");

    @Value("${swiftdrop.frontend-url:http://localhost:5173}")
    private String frontendUrl = "http://localhost:5173";

    public DeliveryAiTools(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Executes tool by name and returns result as a Map or primitive.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> execute(String toolName, Map<String, Object> args, UUID authUserId) {
        log.info("Executing AI Tool: {} with args: {}", toolName, args);
        return switch (toolName) {
            case "calculateDeliveryCost" -> calculateDeliveryCost(args);
            case "trackParcel" -> trackParcel(args);
            case "listMyDeliveries" -> listMyDeliveries(authUserId);
            case "getDeliveryPolicyInfo" -> getDeliveryPolicyInfo(args);
            default -> Map.of("error", "Unknown tool: " + toolName);
        };
    }

    /**
     * Tool 1: Calculate shipping fee based on weight, districts, and delivery type.
     */
    public Map<String, Object> calculateDeliveryCost(Map<String, Object> args) {
        double weight = 1.0;
        Object weightObj = args.get("weightKg");
        if (weightObj instanceof Number n) {
            weight = n.doubleValue();
        } else if (weightObj != null) {
            try {
                weight = Double.parseDouble(weightObj.toString());
            } catch (NumberFormatException ignored) {}
        }
        if (weight <= 0) weight = 1.0;

        String pickupDistrict = Objects.toString(args.getOrDefault("pickupDistrict", "Colombo"), "Colombo").trim();
        String destinationDistrict = Objects.toString(args.getOrDefault("destinationDistrict", "Colombo"), "Colombo").trim();
        String typeStr = Objects.toString(args.getOrDefault("deliveryType", "STANDARD"), "STANDARD").trim().toUpperCase();

        DeliveryType deliveryType = DeliveryType.STANDARD;
        try {
            deliveryType = DeliveryType.valueOf(typeStr);
        } catch (IllegalArgumentException ignored) {}

        boolean isLocal = pickupDistrict.equalsIgnoreCase(destinationDistrict);
        BigDecimal base = isLocal ? sameDistrictBase : otherDistrictBase;

        BigDecimal weightBd = BigDecimal.valueOf(weight);
        BigDecimal extraKg = weightBd.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weightFee = extraKg.multiply(additionalKg).setScale(2, RoundingMode.HALF_UP);

        BigDecimal surcharge = switch (deliveryType) {
            case STANDARD -> BigDecimal.ZERO;
            case EXPRESS -> expressSurcharge;
            case SAME_DAY -> sameDaySurcharge;
        };

        BigDecimal total = base.add(weightFee).add(surcharge).setScale(2, RoundingMode.HALF_UP);

        String estimatedTime = switch (deliveryType) {
            case STANDARD -> "2-3 business days";
            case EXPRESS -> "1-2 business days";
            case SAME_DAY -> "Within 12 hours (same day)";
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", "LKR");
        result.put("weightKg", weight);
        result.put("pickupDistrict", pickupDistrict);
        result.put("destinationDistrict", destinationDistrict);
        result.put("deliveryType", deliveryType.name());
        result.put("baseFee", base);
        result.put("extraWeightFee", weightFee);
        result.put("serviceSurcharge", surcharge);
        result.put("totalEstimatedFee", total);
        result.put("estimatedTimeline", estimatedTime);
        result.put("summary", String.format("Estimated shipping for %.1f kg from %s to %s (%s) is LKR %.2f.",
                weight, pickupDistrict, destinationDistrict, deliveryType, total));
        return result;
    }

    /**
     * Tool 2: Track parcel by tracking number or public token.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> trackParcel(Map<String, Object> args) {
        String trackingNumber = Objects.toString(args.get("trackingNumber"), "").trim();
        if (trackingNumber.isBlank()) {
            return Map.of("found", false, "message", "Please provide a valid tracking number (e.g. SD-829104).");
        }

        List<Delivery> list = entityManager.createQuery(
                "select d from Delivery d where lower(d.trackingNumber) = lower(:tn) or d.publicTrackingToken = :token",
                Delivery.class)
                .setParameter("tn", trackingNumber)
                .setParameter("token", trackingNumber)
                .setMaxResults(1)
                .getResultList();

        if (list.isEmpty()) {
            return Map.of("found", false,
                    "trackingNumber", trackingNumber,
                    "message", "No parcel found matching tracking identifier '" + trackingNumber + "'. Please double check the number.");
        }

        Delivery delivery = list.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("trackingNumber", delivery.getTrackingNumber());
        result.put("status", delivery.getStatus().name());
        result.put("deliveryType", delivery.getDeliveryType() != null ? delivery.getDeliveryType().name() : "STANDARD");

        delivery.getPickupAddress().ifPresent(addr -> {
            result.put("pickupCity", addr.getCity());
            result.put("pickupDistrict", addr.getDistrict());
        });

        delivery.getDestinationAddress().ifPresent(addr -> {
            result.put("destinationCity", addr.getCity());
            result.put("destinationDistrict", addr.getDistrict());
            result.put("recipientName", addr.getContactName());
        });

        if (delivery.getCourier() != null) {
            result.put("courierName", delivery.getCourier().getName().orElse("Assigned Courier"));
        }

        if (delivery.getEstimatedDeliveryDate() != null) {
            result.put("estimatedDeliveryDate", delivery.getEstimatedDeliveryDate().toString());
        }

        // Timeline events
        List<Map<String, String>> timeline = new ArrayList<>();
        for (TrackingEvent event : delivery.getTrackingEvents()) {
            timeline.add(Map.of(
                    "status", event.getStatus().name(),
                    "description", event.getDescription() != null ? event.getDescription() : "",
                    "location", event.getLocationName() != null ? event.getLocationName() : "",
                    "time", event.getEventTime() != null ? event.getEventTime().toString() : ""
            ));
        }
        result.put("timeline", timeline);
        result.put("trackingUrl", frontendUrl + "/track/" + delivery.getPublicTrackingToken());

        return result;
    }

    /**
     * Tool 3: List deliveries for the logged-in customer.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listMyDeliveries(UUID authUserId) {
        if (authUserId == null) {
            return Map.of("authenticated", false,
                    "message", "User is not logged in. Please sign in to view your orders, or provide a specific tracking number to track.");
        }

        List<Delivery> list = entityManager.createQuery(
                "select d from Delivery d join d.customer c where c.user.id = :uid order by d.createdAt desc",
                Delivery.class)
                .setParameter("uid", authUserId)
                .setMaxResults(5)
                .getResultList();

        List<Map<String, Object>> deliveries = new ArrayList<>();
        for (Delivery d : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("trackingNumber", d.getTrackingNumber());
            item.put("status", d.getStatus().name());
            item.put("deliveryType", d.getDeliveryType().name());
            d.getDestinationAddress().ifPresent(dest -> item.put("destinationCity", dest.getCity()));
            item.put("trackingUrl", frontendUrl + "/track/" + d.getPublicTrackingToken());
            deliveries.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", true);
        result.put("totalRecentDeliveries", deliveries.size());
        result.put("deliveries", deliveries);
        return result;
    }

    /**
     * Tool 4: Support & Policy information (cancellation, prohibited items, hours, etc.)
     */
    public Map<String, Object> getDeliveryPolicyInfo(Map<String, Object> args) {
        String topic = Objects.toString(args.getOrDefault("topic", "general"), "general").toLowerCase();

        return switch (topic) {
            case "cancellation" -> Map.of(
                    "topic", "Order Cancellation",
                    "policy", "Customers may cancel a delivery booking for free as long as the status is PENDING or CONFIRMED and no courier has been assigned yet. Once a courier is dispatched, cancellation is locked."
            );
            case "prohibited_items" -> Map.of(
                    "topic", "Prohibited Items",
                    "policy", "SwiftDrop strictly prohibits: explosives, fireworks, flammable liquids/gases, radioactive substances, unsealed perishable goods, firearms/weapons, illegal narcotics, and live animals."
            );
            case "delivery_types" -> Map.of(
                    "topic", "Delivery Speeds",
                    "policy", "1. Standard: 2-3 business days island-wide. 2. Express: 1-2 business days with priority dispatch (+LKR 350). 3. Same Day: Delivery within 12 hours within the same district (+LKR 650)."
            );
            case "support" -> Map.of(
                    "topic", "Customer Support",
                    "policy", "SwiftDrop Support is available Monday to Saturday (8:00 AM - 8:00 PM). Hotline: +94 11 234 5678, Email: support@swiftdrop.me, or visit our Help Center."
            );
            default -> Map.of(
                    "topic", "SwiftDrop Platform",
                    "policy", "SwiftDrop is an island-wide reliable courier platform providing real-time tracking, SMS and email alerts, automated dispatch, and transparent regional pricing."
            );
        };
    }

    /**
     * MCP Tool Definitions for Model Context Protocol consumers.
     */
    public List<McpToolDefinition> getMcpToolDefinitions() {
        return List.of(
                new McpToolDefinition(
                        "calculateDeliveryCost",
                        "Calculates shipping cost in LKR based on weight, pickup district, destination district, and delivery speed.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "weightKg", Map.of("type", "number", "description", "Total weight in kilograms (e.g. 2.5)"),
                                        "pickupDistrict", Map.of("type", "string", "description", "Pickup district (default: Colombo)"),
                                        "destinationDistrict", Map.of("type", "string", "description", "Destination district in Sri Lanka (e.g. Kandy, Galle)"),
                                        "deliveryType", Map.of("type", "string", "enum", List.of("STANDARD", "EXPRESS", "SAME_DAY"), "description", "Delivery speed tier")
                                ),
                                "required", List.of("weightKg", "destinationDistrict")
                        )
                ),
                new McpToolDefinition(
                        "trackParcel",
                        "Tracks a SwiftDrop parcel using its tracking number (e.g. SD-829104) and returns current status, location, timeline, and courier info.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "trackingNumber", Map.of("type", "string", "description", "Tracking number or public token")
                                ),
                                "required", List.of("trackingNumber")
                        )
                ),
                new McpToolDefinition(
                        "listMyDeliveries",
                        "Retrieves recent active parcel deliveries for the currently logged-in customer.",
                        Map.of("type", "object", "properties", Map.of())
                ),
                new McpToolDefinition(
                        "getDeliveryPolicyInfo",
                        "Looks up SwiftDrop policies regarding cancellations, prohibited items, delivery speed types, or customer support contacts.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "topic", Map.of("type", "string", "enum", List.of("cancellation", "prohibited_items", "delivery_types", "support"), "description", "The policy area to look up")
                                ),
                                "required", List.of("topic")
                        )
                )
        );
    }

    /**
     * Formats function declarations for Google Gemini API.
     */
    public List<Map<String, Object>> getGeminiToolDeclarations() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (McpToolDefinition mcp : getMcpToolDefinitions()) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", mcp.name());
            func.put("description", mcp.description());

            // Convert schema keys to uppercase types as required by Gemini
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "OBJECT");
            Map<String, Object> origProps = (Map<String, Object>) mcp.inputSchema().get("properties");
            Map<String, Object> geminiProps = new LinkedHashMap<>();

            if (origProps != null) {
                origProps.forEach((k, v) -> {
                    Map<String, Object> propDef = new LinkedHashMap<>((Map<String, Object>) v);
                    String t = Objects.toString(propDef.get("type"), "string").toUpperCase();
                    propDef.put("type", t.equals("NUMBER") ? "NUMBER" : "STRING");
                    geminiProps.put(k, propDef);
                });
            }
            schema.put("properties", geminiProps);

            Object req = mcp.inputSchema().get("required");
            if (req != null) {
                schema.put("required", req);
            }
            func.put("parameters", schema);
            list.add(func);
        }
        return list;
    }
}

