package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.merchant.dto.CreateMerchantDeliveryRequest;
import com.swiftdrop.backend.merchant.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.merchant.dto.MerchantDeliveryDetailResponse;
import com.swiftdrop.backend.merchant.dto.ParcelRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantRecipientEmailTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void optionalEmailIsNormalizedAndValidated() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var valid = request("  recipient@example.test  ");
            assertThat(valid.recipientEmail()).isEqualTo("recipient@example.test");
            assertThat(validator.validateProperty(valid, "recipientEmail")).isEmpty();
            assertThat(validator.validateProperty(request("invalid"), "recipientEmail")).isNotEmpty();
            assertThat(validator.validateProperty(request("a".repeat(250) + "@example.test"), "recipientEmail")).isNotEmpty();
            assertThat(validator.validateProperty(request(null), "recipientEmail")).isEmpty();
            assertThat(request("  ").recipientEmail()).isNull();
        }
    }

    @Test
    void omissionKeepsLegacyIdempotencySerialization() {
        var omitted = request(null);
        var legacy = new LegacyRequest(omitted.deliveryType(), omitted.pickup(), omitted.destination(),
                omitted.parcels(), omitted.specialInstructions());
        assertThat(json.writeValueAsString(omitted)).isEqualTo(json.writeValueAsString(legacy));
        assertThat(json.writeValueAsString(request("recipient@example.test")))
                .contains("\"recipientEmail\":\"recipient@example.test\"");
    }

    @Test
    void merchantDetailsExposeEmailFromDelivery() {
        var delivery = new Delivery(1L, "SD-EMAIL", "test-token", DeliveryType.STANDARD);
        assertThat(MerchantDeliveryDetailResponse.from(delivery).recipientEmail()).isNull();
        delivery.setRecipientEmail("recipient@example.test");
        assertThat(MerchantDeliveryDetailResponse.from(delivery).recipientEmail()).isEqualTo("recipient@example.test");
    }

    private CreateMerchantDeliveryRequest request(String email) {
        return new CreateMerchantDeliveryRequest(DeliveryType.STANDARD, null, null, List.of(), null, email);
    }

    private record LegacyRequest(DeliveryType deliveryType, DeliveryAddressRequest pickup,
                                 DeliveryAddressRequest destination, List<ParcelRequest> parcels,
                                 String specialInstructions) {
    }
}
