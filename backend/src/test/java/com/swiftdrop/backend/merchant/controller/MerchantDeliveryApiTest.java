package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MerchantDeliveryApiTest {

    private static final String VALID_DELIVERY_JSON = """
            {
              "deliveryType": "EXPRESS",
              "pickup": {
                "contactName": "Sunil Shantha",
                "contactPhone": "0112345678",
                "street": "12 Flower Road",
                "city": "Colombo",
                "district": "Colombo",
                "postalCode": "00700"
              },
              "destination": {
                "contactName": "Kamal Perera",
                "contactPhone": "0812233445",
                "street": "45 Peradeniya Road",
                "city": "Kandy",
                "district": "Kandy",
                "postalCode": "20000"
              },
              "parcels": [
                {
                  "description": "Organic Ceylon Black Tea (500g)",
                  "weightKg": 0.65,
                  "lengthCm": 15.0,
                  "widthCm": 10.0,
                  "heightCm": 8.0,
                  "declaredValue": 2500.00
                }
              ],
              "specialInstructions": "Handle fragile packaging with care"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MerchantDeliveryRepository deliveryRepository;

    @Autowired
    private CustomerDeliveryRepository customerDeliveryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private static RequestPostProcessor merchant(UUID userId, String name) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", name)
                        .claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("merchant:api-access"), new SimpleGrantedAuthority("parcel:create"));
    }

    private static RequestPostProcessor customer(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", "Customer User")
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("parcel:create"), new SimpleGrantedAuthority("parcel:read-own"));
    }

    @Test
    void createDeliverySucceedsAndPersistsMerchantOwnership() throws Exception {
        UUID ownerId = UUID.randomUUID();

        // Ensure merchant profile exists with a business name
        mockMvc.perform(put("/api/merchants/me").with(merchant(ownerId, "Nimal Perera"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"businessName":"Ceylon Tea Exports",
                         "businessEmail":"orders@ceylontea.lk",
                         "contactNumber":"0112345678",
                         "addressLine1":"12 Flower Road",
                         "city":"Colombo",
                         "country":"Sri Lanka"}
                        """)).andExpect(status().isOk());

        // Create order
        MvcResult result = mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(ownerId, "Nimal Perera"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.trackingNumber").isString())
                .andExpect(jsonPath("$.publicTrackingToken").isString())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deliveryType").value("EXPRESS"))
                .andExpect(jsonPath("$.pickup.contactName").value("Sunil Shantha"))
                .andExpect(jsonPath("$.destination.city").value("Kandy"))
                .andExpect(jsonPath("$.parcels[0].description").value("Organic Ceylon Black Tea (500g)"))
                .andExpect(jsonPath("$.trackingEvents[0].status").value("PENDING"))
                .andExpect(jsonPath("$.trackingEvents[0].description").value("Delivery booked by merchant"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long deliveryId = root.get("id").asLong();

        // Verify database row enforces check constraint: merchant_id IS NOT NULL, customer_id IS NULL
        Long merchantId = jdbc.queryForObject("SELECT merchant_id FROM delivery WHERE delivery_id = ?", Long.class, deliveryId);
        Long customerId = jdbc.queryForObject("SELECT customer_id FROM delivery WHERE delivery_id = ?", Long.class, deliveryId);

        assertThat(merchantId).isNotNull();
        assertThat(customerId).isNull();

        // Verify CustomerDeliveryRepository does not count or list this merchant-owned delivery
        assertThat(customerDeliveryRepository.findById(deliveryId)).isPresent();

        // Verify notification event is published to RabbitMQ topic exchange with routing key delivery.status.pending
        ArgumentCaptor<DeliveryStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryStatusChangedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq("delivery.status.pending"),
                eventCaptor.capture()
        );

        DeliveryStatusChangedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.deliveryId()).isEqualTo(deliveryId);
        assertThat(publishedEvent.merchantId()).isEqualTo(merchantId);
        assertThat(publishedEvent.trackingNumber()).isEqualTo(root.get("trackingNumber").asText());
        assertThat(publishedEvent.status()).isEqualTo("PENDING");
        assertThat(publishedEvent.customerName()).isEqualTo("Ceylon Tea Exports");
    }

    @Test
    void createDeliveryValidatesPayloadFields() throws Exception {
        UUID ownerId = UUID.randomUUID();

        // Missing destination
        String invalidMissingDest = """
                {
                  "deliveryType": "STANDARD",
                  "pickup": {
                    "contactName": "Sunil", "contactPhone": "0112345678",
                    "street": "St", "city": "Col", "district": "Col"
                  },
                  "parcels": [
                    { "description": "Tea", "weightKg": 1, "lengthCm": 10, "widthCm": 10, "heightCm": 10 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(ownerId, "Nimal Perera"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMissingDest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.destination").exists());

        // Empty parcels list
        String invalidEmptyParcels = """
                {
                  "deliveryType": "STANDARD",
                  "pickup": {
                    "contactName": "Sunil", "contactPhone": "0112345678",
                    "street": "St", "city": "Col", "district": "Col"
                  },
                  "destination": {
                    "contactName": "Kamal", "contactPhone": "0812233445",
                    "street": "St", "city": "Kandy", "district": "Kandy"
                  },
                  "parcels": []
                }
                """;

        mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(ownerId, "Nimal Perera"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEmptyParcels))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.parcels").exists());
    }

    @Test
    void getDeliveriesWithPaginationAndStatusFilter() throws Exception {
        UUID ownerId = UUID.randomUUID();

        // Create 2 deliveries
        mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(ownerId, "Nimal Perera"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(ownerId, "Nimal Perera"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated());

        // Query all
        mockMvc.perform(get("/api/merchants/me/deliveries?page=0&size=10").with(merchant(ownerId, "Nimal"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0));

        // Query status filter
        mockMvc.perform(get("/api/merchants/me/deliveries?status=PENDING").with(merchant(ownerId, "Nimal"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/merchants/me/deliveries?status=DELIVERED").with(merchant(ownerId, "Nimal"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void merchantIsolationPreventsAccessToOtherMerchantsDeliveries() throws Exception {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();

        // Merchant A creates a delivery
        MvcResult res = mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(merchantA, "Merchant A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        long deliveryIdA = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Merchant A can read their own delivery
        mockMvc.perform(get("/api/merchants/me/deliveries/" + deliveryIdA).with(merchant(merchantA, "Merchant A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryIdA));

        // Merchant B tries to read Merchant A's delivery -> 404 Not Found
        mockMvc.perform(get("/api/merchants/me/deliveries/" + deliveryIdA).with(merchant(merchantB, "Merchant B")))
                .andExpect(status().isNotFound());

        // Merchant B lists their deliveries -> empty
        mockMvc.perform(get("/api/merchants/me/deliveries").with(merchant(merchantB, "Merchant B")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void dashboardAggregatesStatusCountsAndRecentOrders() throws Exception {
        UUID ownerId = UUID.randomUUID();

        // Create merchant profile
        mockMvc.perform(put("/api/merchants/me").with(merchant(ownerId, "Merchant Dashboard Owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"businessName":"Ceylon Spice House",
                         "businessEmail":"info@ceylonspice.lk",
                         "contactNumber":"0114455667",
                         "addressLine1":"88 Spice Lane",
                         "city":"Matara",
                         "country":"Sri Lanka"}
                        """)).andExpect(status().isOk());

        // Create 1 delivery
        mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(ownerId, "Owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated());

        // Get dashboard
        mockMvc.perform(get("/api/merchants/me/dashboard").with(merchant(ownerId, "Owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Ceylon Spice House"))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.pendingOrders").value(1))
                .andExpect(jsonPath("$.inTransitOrders").value(0))
                .andExpect(jsonPath("$.deliveredOrders").value(0))
                .andExpect(jsonPath("$.recentOrders.length()").value(1))
                .andExpect(jsonPath("$.recentOrders[0].destinationCity").value("Kandy"));
    }

    @Test
    void dashboardReflectsIncompleteProfile() throws Exception {
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(get("/api/merchants/me/dashboard").with(merchant(ownerId, "New Merchant")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.totalOrders").value(0));
    }

    @Test
    void customerWithoutMerchantEntitlementCannotAccessMerchantOrdersApi() throws Exception {
        UUID customerId = UUID.randomUUID();

        mockMvc.perform(get("/api/merchants/me/deliveries").with(customer(customerId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/merchants/me/deliveries").with(customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_DELIVERY_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/merchants/me/dashboard").with(customer(customerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleOnlyMerchantWithoutEntitlementIsDenied() throws Exception {
        UUID userId = UUID.randomUUID();
        RequestPostProcessor roleOnly = jwt().jwt(token -> token.subject(userId.toString())
                        .claim("name", "Unapproved Merchant")
                        .claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));

        mockMvc.perform(get("/api/merchants/me/deliveries").with(roleOnly))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/merchants/me/dashboard").with(roleOnly))
                .andExpect(status().isForbidden());
    }
}
