package com.swiftdrop.backend.ai;

import com.swiftdrop.backend.ai.dto.McpToolDefinition;
import com.swiftdrop.backend.ai.tool.DeliveryAiTools;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAiToolsTest {

    private EntityManager entityManager;
    private DeliveryAiTools tools;

    @BeforeEach
    void setUp() {
        entityManager = Mockito.mock(EntityManager.class);
        tools = new DeliveryAiTools(entityManager);
    }

    @Test
    void calculatesStandardPricingSameDistrict() {
        Map<String, Object> result = tools.calculateDeliveryCost(Map.of(
                "weightKg", 1.0,
                "pickupDistrict", "Colombo",
                "destinationDistrict", "Colombo",
                "deliveryType", "STANDARD"
        ));

        assertThat(result.get("currency")).isEqualTo("LKR");
        assertThat((BigDecimal) result.get("baseFee")).isEqualByComparingTo("350.00");
        assertThat((BigDecimal) result.get("extraWeightFee")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) result.get("serviceSurcharge")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) result.get("totalEstimatedFee")).isEqualByComparingTo("350.00");
    }

    @Test
    void calculatesExpressPricingOtherDistrictWithExtraWeight() {
        // 2 kg from Colombo to Kandy, Express:
        // Base: 650.00, Extra 1 kg: 120.00, Express: 350.00 -> Total: 1120.00
        Map<String, Object> result = tools.calculateDeliveryCost(Map.of(
                "weightKg", 2.0,
                "pickupDistrict", "Colombo",
                "destinationDistrict", "Kandy",
                "deliveryType", "EXPRESS"
        ));

        assertThat(result.get("currency")).isEqualTo("LKR");
        assertThat((BigDecimal) result.get("baseFee")).isEqualByComparingTo("650.00");
        assertThat((BigDecimal) result.get("extraWeightFee")).isEqualByComparingTo("120.00");
        assertThat((BigDecimal) result.get("serviceSurcharge")).isEqualByComparingTo("350.00");
        assertThat((BigDecimal) result.get("totalEstimatedFee")).isEqualByComparingTo("1120.00");
    }

    @Test
    void retrievesMcpToolDefinitions() {
        List<McpToolDefinition> definitions = tools.getMcpToolDefinitions();

        assertThat(definitions).hasSize(4);
        assertThat(definitions).extracting(McpToolDefinition::name)
                .containsExactlyInAnyOrder("calculateDeliveryCost", "trackParcel", "listMyDeliveries", "getDeliveryPolicyInfo");
    }

    @Test
    void returnsPolicyInfoForProhibitedItems() {
        Map<String, Object> result = tools.getDeliveryPolicyInfo(Map.of("topic", "prohibited_items"));

        assertThat(result.get("topic")).isEqualTo("Prohibited Items");
        assertThat(result.get("policy").toString()).contains("explosives");
    }
}

