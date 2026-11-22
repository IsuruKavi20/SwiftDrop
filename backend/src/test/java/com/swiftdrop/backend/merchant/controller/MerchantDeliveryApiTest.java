package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.repository.CustomerDeliveryRepository;
import com.swiftdrop.backend.merchant.repository.MerchantDeliveryRepository;
import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.pricing.DistanceSource;
import com.swiftdrop.backend.notification.event.DeliveryStatusChangedEvent;
import com.swiftdrop.backend.notification.event.MerchantConfirmedWebhook;
import com.swiftdrop.backend.merchant.security.MerchantMachineTokenContract;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.Duration;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MerchantDeliveryApiTest.TestIssuer.class)
@Transactional
class MerchantDeliveryApiTest {

    static final String VALID_DELIVERY_JSON = """
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
    private PlatformTransactionManager transactions;

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

    @MockitoBean
    private DistanceSource distanceSource;

    private static final String MACHINE_BOOKING = "/api/merchant-integrations/deliveries";

    @TestConfiguration
    static class TestIssuer {
        // These fields exist only in tests. No production adapter claims this issuer contract.
        @Bean
        MerchantMachineTokenContract testContract() {
            return new MerchantMachineTokenContract() {
                @Override
                public boolean isMachine(Jwt token) {
                    return "test-machine".equals(token.getClaimAsString("test_kind"));
                }

                @Override
                public Optional<BookingIdentity> authorizeBooking(Jwt token) {
                    String client = token.getClaimAsString("test_client");
                    String owner = token.getClaimAsString("test_owner");
                    if (!Boolean.TRUE.equals(token.getClaimAsBoolean("test_book")) || owner == null
                            || client == null || client.isBlank()) {
                        return Optional.empty();
                    }
                    return Optional.of(new BookingIdentity(UUID.fromString(owner), client));
                }
            };
        }
    }

    private static RequestPostProcessor machine(UUID owner, String client, boolean allowed) {
        // Deliberately UUID-shaped subject and human authority to exercise the human-route guard.
        return jwt().jwt(token -> token.subject(owner.toString())
                        .claim("test_kind", "test-machine").claim("test_client", client)
                        .claim("test_owner", owner.toString()).claim("test_book", allowed))
                .authorities(new SimpleGrantedAuthority("merchant:api-access"));
    }

    private void createProfile(UUID owner) throws Exception {
        mockMvc.perform(get("/api/merchants/me").with(merchant(owner, "Business owner")))
                .andExpect(status().isOk());
    }

    private MvcResult machineBooking(RequestPostProcessor caller, String key, String body, int expected) throws Exception {
        return mockMvc.perform(post(MACHINE_BOOKING).with(caller).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn();
    }

    @Test
    void machineBookingUsesTrustedOwnerAndSharedFeeAndPersistsAudit() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        createProfile(owner);
        createProfile(other);
        org.mockito.Mockito.when(distanceSource.drivingDistance(
                        "12 Flower Road, Colombo, Colombo District, 00700, Sri Lanka",
                        "45 Peradeniya Road, Kandy, Kandy District, 20000, Sri Lanka"))
                .thenReturn(Optional.of(new DistanceSource.RouteDistance(new BigDecimal("10.0"), Duration.ofMinutes(20))));
        var caller = machine(owner, "test-shop-client", true);
        String base = "/api/merchants/me/deliveries";

        MvcResult response = machineBooking(caller, "sale-42", VALID_DELIVERY_JSON, 201);
        JsonNode created = objectMapper.readTree(response.getResponse().getContentAsString());
        long id = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(created.path("estimatedFee").decimalValue()).isEqualByComparingTo(new BigDecimal("630.00"));
        assertThat(created.path("trackingNumber").asText()).startsWith("SD-");
        assertThat(created.path("trackingEvents").get(0).path("description").asText())
                .isEqualTo("Delivery booked via Merchant API");

        var personal = mockMvc.perform(post(base).with(merchant(owner, "Business owner"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated()).andReturn();
        assertThat(created.path("estimatedFee").decimalValue())
                .isEqualByComparingTo(objectMapper.readTree(personal.getResponse().getContentAsString())
                        .path("estimatedFee").decimalValue());

        Long merchantId = jdbc.queryForObject("SELECT merchant_id FROM merchant WHERE auth_user_id = ?", Long.class, owner);
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM delivery WHERE delivery_id = ?", Long.class, id))
                .isEqualTo(merchantId);
        assertThat(jdbc.queryForObject("SELECT estimated_fee FROM delivery WHERE delivery_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo(created.path("estimatedFee").decimalValue());
        assertThat(jdbc.queryForObject("SELECT client_id FROM merchant_api_booking WHERE delivery_id = ?",
                String.class, id)).isEqualTo("test-shop-client");
        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM merchant_api_booking WHERE delivery_id = ?",
                String.class, id)).isEqualTo("sale-42");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tracking_event WHERE delivery_id = ? AND recorded_by IS NULL",
                Integer.class, id)).isEqualTo(1);

        var replay = machineBooking(caller, "sale-42", VALID_DELIVERY_JSON, 200);
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).path("id").asLong()).isEqualTo(id);
        machineBooking(caller, "sale-42", VALID_DELIVERY_JSON.replace("EXPRESS", "STANDARD"), 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_api_booking WHERE merchant_id = ?",
                Integer.class, merchantId)).isEqualTo(1);

        mockMvc.perform(get(base).with(merchant(owner, "Business owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/merchants/me/dashboard").with(merchant(owner, "Business owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingOrders").value(2));
        mockMvc.perform(get(base + "/" + id).with(merchant(other, "Other owner"))).andExpect(status().isNotFound());
        mockMvc.perform(post(base + "/" + id + "/confirm").with(caller)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/merchants/me").with(caller).contentType(MediaType.APPLICATION_JSON).content("""
                {"businessName":"Shop","businessEmail":"shop@example.test","contactNumber":"0112345678",
                 "addressLine1":"Main Road","city":"Colombo","country":"Sri Lanka"}
                """)).andExpect(status().isForbidden());
        mockMvc.perform(post(base + "/" + id + "/confirm").with(merchant(owner, "Business owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectsMissingPermissionUnknownOwnerAndInvalidRequests() throws Exception {
        UUID owner = UUID.randomUUID();
        createProfile(owner);
        mockMvc.perform(post(MACHINE_BOOKING).header("Idempotency-Key", "sale-42")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON))
                .andExpect(status().isUnauthorized());
        machineBooking(merchant(owner, "Owner"), "sale-42", VALID_DELIVERY_JSON, 403);
        machineBooking(machine(owner, "test-denied", false), "sale-42", VALID_DELIVERY_JSON, 403);
        machineBooking(machine(UUID.randomUUID(), "test-unknown", true), "sale-42", VALID_DELIVERY_JSON, 403);
        machineBooking(machine(owner, "", true), "sale-42", VALID_DELIVERY_JSON, 403);
        machineBooking(machine(owner, "test-shop", true), "not a valid key", VALID_DELIVERY_JSON, 400);
        machineBooking(machine(owner, "test-shop", true), "sale-42", VALID_DELIVERY_JSON.replace("0.65", "0"), 400);
        mockMvc.perform(post(MACHINE_BOOKING).with(machine(owner, "test-shop", true))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/merchants/me/deliveries").with(merchant(owner, "Owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void clientCannotSelectAnotherMerchantThroughTheBookingBody() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        createProfile(owner);
        createProfile(other);
        Long otherMerchantId = jdbc.queryForObject("SELECT merchant_id FROM merchant WHERE auth_user_id = ?",
                Long.class, other);
        String body = VALID_DELIVERY_JSON.replaceFirst("\\{", "{\"merchantId\":" + otherMerchantId + ",");
        var booked = machineBooking(machine(owner, "test-shop", true), "spoofed-owner", body, 201);
        long id = objectMapper.readTree(booked.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(get("/api/merchants/me/deliveries/" + id).with(merchant(owner, "Owner")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/merchants/me/deliveries/" + id).with(merchant(other, "Other")))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetriesCommitOnlyOneShipment() throws Exception {
        UUID owner = UUID.randomUUID();
        try {
            createProfile(owner);

            try (var workers = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<MvcResult> submit = () -> mockMvc.perform(post(MACHINE_BOOKING)
                        .with(machine(owner, "test-concurrent", true)).header("Idempotency-Key", "same-sale")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON)).andReturn();
                var first = workers.submit(submit);
                var second = workers.submit(submit);
                MvcResult a = first.get(15, TimeUnit.SECONDS);
                MvcResult b = second.get(15, TimeUnit.SECONDS);
                assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(201, 200);
                assertThat(objectMapper.readTree(a.getResponse().getContentAsString()).path("id").asLong())
                        .isEqualTo(objectMapper.readTree(b.getResponse().getContentAsString()).path("id").asLong());
            }
            mockMvc.perform(get("/api/merchants/me/deliveries").with(merchant(owner, "Owner")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        } finally {
            // Worker requests commit independently of Spring's test rollback. Remove only this owner’s fixtures.
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                String business = "(SELECT merchant_id FROM merchant WHERE auth_user_id = ?)";
                jdbc.update("DELETE FROM merchant_api_booking WHERE merchant_id = " + business, owner);
                jdbc.update("DELETE FROM delivery WHERE merchant_id = " + business, owner);
                jdbc.update("DELETE FROM merchant WHERE auth_user_id = ?", owner);
                jdbc.update("DELETE FROM app_users WHERE id = ?", owner);
            });
        }
    }

    @Test
    void humanBookingStoresRecipientEmailAndReturnsItOnlyToTheOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        String body = VALID_DELIVERY_JSON.replaceFirst("\\{", "{\"recipientEmail\":\" recipient@example.test \",");
        var created = humanBooking(owner, "email-row", body);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode delivery = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = delivery.path("id").asLong();
        assertThat(delivery.path("recipientEmail").asText()).isEqualTo("recipient@example.test");
        assertThat(jdbc.queryForObject("SELECT recipient_email FROM delivery WHERE delivery_id = ?", String.class, id))
                .isEqualTo("recipient@example.test");

        mockMvc.perform(get("/api/merchants/me/deliveries/" + id).with(merchant(owner, "Owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientEmail").value("recipient@example.test"));
        mockMvc.perform(get("/api/merchants/me/deliveries/" + id).with(merchant(UUID.randomUUID(), "Other")))
                .andExpect(status().isNotFound());
        assertThat(humanBooking(owner, "email-row", body.replace(" recipient@example.test ", "recipient@example.test"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(humanBooking(owner, "email-row", body.replace("recipient@example.test", "other@example.test"))
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(humanBooking(owner, "invalid-email-row", body.replace("recipient@example.test", "invalid"))
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void humanBookingReplaysNormalizedRequestAndIsolatesMerchants() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        MvcResult created = humanBooking(owner, "row-1", VALID_DELIVERY_JSON);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode delivery = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = delivery.path("id").asLong();
        assertThat(delivery.path("recipientEmail").isNull()).isTrue();
        assertThat(delivery.path("status").asText()).isEqualTo("PENDING");
        assertThat(delivery.path("trackingNumber").asText()).isNotBlank();
        assertThat(delivery.path("estimatedFee").asDouble()).isPositive();
        assertThat(delivery.path("trackingEvents").size()).isEqualTo(1);

        MvcResult replay = humanBooking(owner, "row-1",
                VALID_DELIVERY_JSON.replace("Sunil Shantha", " Sunil Shantha ").replace("null", "\"\""));
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString())).isEqualTo(delivery);
        assertThat(humanBooking(owner, "row-1", VALID_DELIVERY_JSON.replace("EXPRESS", "STANDARD"))
                .getResponse().getStatus()).isEqualTo(409);
        var otherBooking = humanBooking(other, "row-1", VALID_DELIVERY_JSON);
        assertThat(otherBooking.getResponse().getStatus()).isEqualTo(201);
        assertThat(objectMapper.readTree(otherBooking.getResponse().getContentAsString()).path("id").asLong())
                .isNotEqualTo(id);
        mockMvc.perform(post("/api/merchants/me/deliveries/" + id + "/confirm").with(merchant(owner, "Owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        var confirmed = humanBooking(owner, "row-1", VALID_DELIVERY_JSON);
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(confirmed.getResponse().getContentAsString()).path("status").asText())
                .isEqualTo("CONFIRMED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentHumanRetriesCreateOneDeliveryAndPublishOnce() throws Exception {
        UUID owner = UUID.randomUUID();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status ->
                    com.swiftdrop.backend.identity.entity.TestPeople.saved(entityManager, owner, "Owner", "MERCHANT"));
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> humanBooking(owner, "same-row", VALID_DELIVERY_JSON));
                var second = workers.submit(() -> humanBooking(owner, "same-row", VALID_DELIVERY_JSON));
                var a = first.get(15, TimeUnit.SECONDS);
                var b = second.get(15, TimeUnit.SECONDS);
                assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                        .containsExactlyInAnyOrder(201, 200);
                assertThat(objectMapper.readTree(a.getResponse().getContentAsString()).path("id"))
                        .isEqualTo(objectMapper.readTree(b.getResponse().getContentAsString()).path("id"));
            }
            mockMvc.perform(get("/api/merchants/me/deliveries").with(merchant(owner, "Owner")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                    eq("delivery.status.pending"), any(DeliveryStatusChangedEvent.class));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM delivery WHERE merchant_id = (SELECT merchant_id FROM merchant WHERE auth_user_id = ?)", owner);
                jdbc.update("DELETE FROM merchant WHERE auth_user_id = ?", owner);
                jdbc.update("DELETE FROM app_users WHERE id = ?", owner);
            });
        }
    }

    private MvcResult humanBooking(UUID owner, String key, String request) throws Exception {
        return mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(owner, "Owner"))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(request)).andReturn();
    }

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
    void publishedTariffPricesQuotesAndBookingsForEveryService() throws Exception {
        var admin = jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var owner = merchant(UUID.randomUUID(), "Merchant");
        mockMvc.perform(post("/api/admin/pricing").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"distanceBase":200,"includedKm":5,"perKm":10,"sameDistrictBase":100,"otherDistrictBase":300,"additionalKg":80,
                                 "expressSurcharge":120,"sameDaySurcharge":200}
                                """))
                .andExpect(status().isCreated());

        String weighted = VALID_DELIVERY_JSON.replace("0.65", "2.25");
        String from = "12 Flower Road, Colombo, Colombo District, 00700, Sri Lanka";
        String to = "45 Peradeniya Road, Kandy, Kandy District, 20000, Sri Lanka";
        org.mockito.Mockito.when(distanceSource.drivingDistance(from, to)).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/merchants/me/deliveries/estimate").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content(weighted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").isEmpty())
                .andExpect(jsonPath("$.totalFee").value(520));

        org.mockito.Mockito.when(distanceSource.drivingDistance(from, to))
                .thenReturn(Optional.of(new DistanceSource.RouteDistance(new BigDecimal("10.44"), Duration.ofMinutes(25))));
        for (var service : java.util.Map.of("STANDARD", 354, "EXPRESS", 474, "SAME_DAY", 554).entrySet()) {
            String payload = weighted.replace("EXPRESS", service.getKey());
            mockMvc.perform(post("/api/merchants/me/deliveries/estimate").with(owner)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.distanceKm").value(10.4))
                    .andExpect(jsonPath("$.routeFrom").value(from))
                    .andExpect(jsonPath("$.routeTo").value(to))
                    .andExpect(jsonPath("$.distanceCharge").value(54))
                    .andExpect(jsonPath("$.totalFee").value(service.getValue()));
            mockMvc.perform(post("/api/customers/me/deliveries/estimate").with(customer(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalFee").value(service.getValue()));
            // The single-shipment and CSV clients use this same booking endpoint.
            MvcResult result = mockMvc.perform(post("/api/merchants/me/deliveries").with(owner)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estimatedFee").value(service.getValue()))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn();
            long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
            assertThat(jdbc.queryForObject("SELECT estimated_fee FROM delivery WHERE delivery_id = ?", BigDecimal.class, id))
                    .isEqualByComparingTo(BigDecimal.valueOf(service.getValue()));
        }

        mockMvc.perform(post("/api/merchants/me/deliveries/estimate").with(customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(weighted))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerConfirmsOnceAndOrderEntersDispatchQueueAndPublicHistory() throws Exception {
        var owner = merchant(UUID.randomUUID(), "Owner");
        MvcResult result = mockMvc.perform(post("/api/merchants/me/deliveries").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON))
                .andExpect(status().isCreated()).andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = created.get("id").asLong();
        String confirm = "/api/merchants/me/deliveries/" + id + "/confirm";
        mockMvc.perform(post(confirm)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(confirm).with(customer(UUID.randomUUID()))).andExpect(status().isForbidden());
        mockMvc.perform(post(confirm).with(merchant(UUID.randomUUID(), "Other"))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/merchants/me/deliveries/0/confirm").with(owner)).andExpect(status().isNotFound());
        mockMvc.perform(post(confirm).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.estimatedFee").value(created.get("estimatedFee").asDouble()))
                .andExpect(jsonPath("$.trackingEvents[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.trackingEvents[1].description").value("Booking confirmed by merchant"));
        mockMvc.perform(get("/api/dispatcher/deliveries/unassigned").with(jwt().jwt(token ->
                        token.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]", id).isNotEmpty());
        mockMvc.perform(get("/api/public/tracking/" + created.get("trackingNumber").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.events[1].status").value("CONFIRMED"));
        assertThat(mockingDetails(rabbitTemplate).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("convertAndSend"))
                .map(invocation -> invocation.getArgument(1, String.class)))
                .doesNotContain("delivery.status.confirmed", MerchantConfirmedWebhook.ROUTING_KEY);
        mockMvc.perform(post(confirm).with(owner)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tracking_event WHERE delivery_id = ?", Integer.class, id))
                .isEqualTo(2);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(rabbitTemplate, after(250).never()).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantConfirmedWebhook.ROUTING_KEY), any(MerchantConfirmedWebhook.class), any(CorrelationData.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedConfirmationQueuesOneMerchantWebhook() throws Exception {
        UUID ownerId = UUID.randomUUID();
        var owner = merchant(ownerId, "Owner");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                eq(MerchantConfirmedWebhook.ROUTING_KEY), any(MerchantConfirmedWebhook.class), any(CorrelationData.class));

        try {
            MvcResult created = mockMvc.perform(post("/api/merchants/me/deliveries").with(owner)
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode shipment = objectMapper.readTree(created.getResponse().getContentAsString());
            long id = shipment.path("id").asLong();
            String confirm = "/api/merchants/me/deliveries/" + id + "/confirm";

            mockMvc.perform(post(confirm).with(owner)).andExpect(status().isOk());
            mockMvc.perform(post(confirm).with(owner)).andExpect(status().isConflict());

            var envelope = ArgumentCaptor.forClass(MerchantConfirmedWebhook.class);
            verify(rabbitTemplate, timeout(5000)).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                    eq(MerchantConfirmedWebhook.ROUTING_KEY), envelope.capture(), any(CorrelationData.class));
            verify(rabbitTemplate, timeout(5000)).convertAndSend(eq(RabbitMqConfig.EXCHANGE_SWIFTDROP),
                    eq("delivery.status.confirmed"), any(DeliveryStatusChangedEvent.class));

            Long trackingId = jdbc.queryForObject(
                    "SELECT tracking_event_id FROM tracking_event WHERE delivery_id = ? AND status = 'CONFIRMED'",
                    Long.class, id);
            assertThat(envelope.getValue().merchantId()).isEqualTo(jdbc.queryForObject(
                    "SELECT merchant_id FROM delivery WHERE delivery_id = ?", Long.class, id));
            assertThat(envelope.getValue().payload().eventId()).isEqualTo("delivery-status-" + trackingId);
            assertThat(envelope.getValue().payload().deliveryId()).isEqualTo(id);
            assertThat(envelope.getValue().payload().trackingNumber()).isEqualTo(shipment.path("trackingNumber").asText());
            assertThat(envelope.getValue().payload().status()).isEqualTo("CONFIRMED");
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM delivery WHERE merchant_id = (SELECT merchant_id FROM merchant WHERE auth_user_id = ?)", ownerId);
                jdbc.update("DELETE FROM merchant WHERE auth_user_id = ?", ownerId);
                jdbc.update("DELETE FROM app_users WHERE id = ?", ownerId);
            });
        }
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void freshMerchantReadsReturnEmptyResultsWithoutCreatingAProfile() throws Exception {
        UUID fresh = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var freshCaller = merchant(fresh, "New Merchant");
        try {
            var booked = mockMvc.perform(post("/api/merchants/me/deliveries").with(merchant(other, "Other Merchant"))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_DELIVERY_JSON))
                    .andExpect(status().isCreated()).andReturn();
            long otherDelivery = objectMapper.readTree(booked.getResponse().getContentAsString()).path("id").asLong();

            mockMvc.perform(get("/api/merchants/me/dashboard").with(freshCaller))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileComplete").value(false))
                    .andExpect(jsonPath("$.totalOrders").value(0))
                    .andExpect(jsonPath("$.countsByStatus.PENDING").value(0))
                    .andExpect(jsonPath("$.recentOrders").isEmpty());
            mockMvc.perform(get("/api/merchants/me/deliveries?status=CONFIRMED&page=2&size=5").with(freshCaller))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(5))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
            mockMvc.perform(get("/api/merchants/me/deliveries/" + otherDelivery).with(freshCaller))
                    .andExpect(status().isNotFound());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant WHERE auth_user_id = ?", Integer.class, fresh))
                    .isZero();
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM delivery WHERE merchant_id = (SELECT merchant_id FROM merchant WHERE auth_user_id = ?)", other);
                jdbc.update("DELETE FROM merchant WHERE auth_user_id IN (?, ?)", fresh, other);
                jdbc.update("DELETE FROM app_users WHERE id IN (?, ?)", fresh, other);
            });
        }
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
