package com.swiftdrop.backend.pricing.controller;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Pricing page's API, and that quotes follow a published tariff while bookings keep their fee. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPricingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final UUID adminId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();

    /** Colombo to Kandy, Express, 2.5 kg: other-district base + 1.5 kg extra + Express surcharge. */
    private static final String BOOKING = """
            {
              "deliveryType": "EXPRESS",
              "pickup":      {"contactName":"Alice Fernando","contactPhone":"0771111111","street":"1 Main St",
                              "city":"Colombo","district":"Colombo","postalCode":"00100"},
              "destination": {"contactName":"Kamal Perera","contactPhone":"0719999999","street":"5 Temple Rd",
                              "city":"Kandy","district":"Kandy"},
              "parcels": [{"description":"Books","weightKg":2.5,"lengthCm":30,"widthCm":20,"heightCm":10}]
            }""";

    private static final String NEW_RATES = """
            {"sameDistrictBase":400,"otherDistrictBase":700,"additionalKg":100,"expressSurcharge":400,"sameDaySurcharge":800}""";

    @BeforeEach
    void setUp() {
        TestPeople.saved(em, adminId, "Amaya Admin", "ADMIN");
    }

    @Test
    void onlyAdminsSeeOrPublishPricing() throws Exception {
        mockMvc.perform(get("/api/admin/pricing")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/pricing").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/pricing").with(customer()).contentType(MediaType.APPLICATION_JSON).content(NEW_RATES))
                .andExpect(status().isForbidden());
    }

    @Test
    void beforeAnyPublishTheServerSettingsApply() throws Exception {
        mockMvc.perform(get("/api/admin/pricing").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.publishedBy").doesNotExist())
                .andExpect(jsonPath("$.rates.sameDistrictBase").value(350.0))
                .andExpect(jsonPath("$.rates.otherDistrictBase").value(650.0))
                .andExpect(jsonPath("$.rates.additionalKg").value(120.0))
                .andExpect(jsonPath("$.rates.expressSurcharge").value(350.0))
                .andExpect(jsonPath("$.rates.sameDaySurcharge").value(650.0));
    }

    @Test
    void publishingAddsVersionsAndQuotesUseTheNewest() throws Exception {
        quote().andExpect(jsonPath("$.totalFee").value(1180.0))
                .andExpect(jsonPath("$.explanation").value("Standard SwiftDrop rates: district base + additional weight + service surcharge."));

        publish(NEW_RATES)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.publishedBy").value("Amaya Admin"))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.rates.otherDistrictBase").value(700.0));
        // 700 + 1.5 kg × 100 + 400
        quote().andExpect(jsonPath("$.totalFee").value(1250.0))
                .andExpect(jsonPath("$.baseFee").value(700.0))
                .andExpect(jsonPath("$.weightCharge").value(150.0))
                .andExpect(jsonPath("$.serviceCharge").value(400.0))
                .andExpect(jsonPath("$.explanation").value("SwiftDrop rates (version 1): district base + additional weight + service surcharge."));

        publish(NEW_RATES.replace("\"otherDistrictBase\":700", "\"otherDistrictBase\":750.5"))
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/admin/pricing").with(admin()))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.rates.otherDistrictBase").value(750.5));
        quote().andExpect(jsonPath("$.totalFee").value(1300.5));
    }

    @Test
    void parcelsAlreadyBookedKeepTheirFee() throws Exception {
        registerAlice();
        String booked = mockMvc.perform(post("/api/customers/me/deliveries").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedFee").value(1180.0))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(booked, "$.id");

        publish(NEW_RATES).andExpect(status().isCreated());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/customers/me/deliveries/{id}", id).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedFee").value(1180.0));
        // A new booking uses the new rates
        mockMvc.perform(post("/api/customers/me/deliveries").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(jsonPath("$.estimatedFee").value(1250.0));
    }

    @Test
    void refusesRatesThatAreMissingNegativeOrTooLarge() throws Exception {
        publish("{\"sameDistrictBase\":400,\"otherDistrictBase\":-1,\"additionalKg\":100,\"expressSurcharge\":400}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.otherDistrictBase").value("Rates must be zero or more"))
                .andExpect(jsonPath("$.errors.sameDaySurcharge").value("Enter the Same day surcharge"));
        publish(NEW_RATES.replace("\"additionalKg\":100", "\"additionalKg\":1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.additionalKg").exists());
        publish(NEW_RATES.replace("\"additionalKg\":100", "\"additionalKg\":10.555"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.additionalKg").value("Use at most 2 decimals"));
        mockMvc.perform(get("/api/admin/pricing").with(admin())).andExpect(jsonPath("$.version").value(0));
    }

    private ResultActions publish(String rates) throws Exception {
        return mockMvc.perform(post("/api/admin/pricing").with(admin()).contentType(MediaType.APPLICATION_JSON).content(rates));
    }

    private ResultActions quote() throws Exception {
        return mockMvc.perform(post("/api/customers/me/deliveries/estimate").with(customer())
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isOk());
    }

    private void registerAlice() throws Exception {
        mockMvc.perform(post("/api/customers/me").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"0771234567","address":{"label":"Home","street":"12 Galle Road","city":"Colombo","district":"Colombo"}}"""))
                .andExpect(status().isCreated());
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject(adminId.toString()).claim("name", "Amaya Admin").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor customer() {
        return jwt().jwt(t -> t.subject(alice.toString()).claim("name", "Alice Fernando").claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("parcel:create"),
                        new SimpleGrantedAuthority("parcel:read-own"));
    }
}
