package com.swiftdrop.backend.ai;

import com.swiftdrop.backend.ai.dto.McpToolDefinition;
import com.swiftdrop.backend.ai.tool.DeliveryAiTools;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void trackingAnswerHasNoPersonalDetailsOrSecretLink() {
        // A parcel whose receiver and courier have names, and which has a secret SMS-link token
        DeliveryAddress destination = Mockito.mock(DeliveryAddress.class);
        Mockito.when(destination.getCity()).thenReturn("Kandy");
        Mockito.when(destination.getDistrict()).thenReturn("Kandy");
        Mockito.when(destination.getContactName()).thenReturn("Receiver Name");
        Mockito.when(destination.getContactPhone()).thenReturn("0771234567");
        Delivery delivery = Mockito.mock(Delivery.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(delivery.getTrackingNumber()).thenReturn("SD-ABC123");
        Mockito.when(delivery.getPublicTrackingToken()).thenReturn("secret-link-token");
        Mockito.when(delivery.getStatus()).thenReturn(DeliveryStatus.IN_TRANSIT);
        Mockito.when(delivery.getDeliveryType()).thenReturn(DeliveryType.STANDARD);
        Mockito.when(delivery.getPickupAddress()).thenReturn(Optional.empty());
        Mockito.when(delivery.getDestinationAddress()).thenReturn(Optional.of(destination));
        Mockito.when(delivery.getTrackingEvents()).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        TypedQuery<Delivery> query = Mockito.mock(TypedQuery.class);
        Mockito.when(entityManager.createQuery(Mockito.anyString(), Mockito.eq(Delivery.class))).thenReturn(query);
        Mockito.when(query.setParameter(Mockito.anyString(), Mockito.any())).thenReturn(query);
        Mockito.when(query.setMaxResults(Mockito.anyInt())).thenReturn(query);
        Mockito.when(query.getResultList()).thenReturn(List.of(delivery));

        Map<String, Object> result = tools.trackParcel(Map.of("trackingNumber", "SD-ABC123"));

        String everything = result.toString();
        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("destinationCity")).isEqualTo("Kandy");
        assertThat(everything).doesNotContain("Receiver Name", "0771234567", "secret-link-token");
        assertThat(result).doesNotContainKeys("recipientName", "courierName");
    }

    @Test
    void returnsPolicyInfoForProhibitedItems() {
        Map<String, Object> result = tools.getDeliveryPolicyInfo(Map.of("topic", "prohibited_items"));

        assertThat(result.get("topic")).isEqualTo("Prohibited Items");
        assertThat(result.get("policy").toString()).contains("explosives");
    }
}

