package com.swiftdrop.backend.merchant.controller;

import com.swiftdrop.backend.merchant.entity.Merchant;
import com.swiftdrop.backend.merchant.repository.MerchantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MerchantApiTest {

    private static final String COMPLETE_PROFILE = """
            {"businessName":"Ceylon Tea House","businessEmail":"orders@ceylontea.lk","contactNumber":"0112345678",
             "addressLine1":"12 Galle Road","city":"Colombo","country":"Sri Lanka"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MerchantRepository merchantRepository;

    private final UUID nimal = UUID.randomUUID();

    /** An approved merchant (a person): role MERCHANT with its permissions, as the auth service sends them. */
    private static RequestPostProcessor merchant(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", "Nimal Perera")
                        .claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("merchant:api-access"), new SimpleGrantedAuthority("parcel:create"));
    }

    @Test
    void theFirstVisitCreatesAnEmptyProfile() throws Exception {
        mockMvc.perform(get("/api/merchants/me").with(merchant(nimal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.ownerName").value("Nimal Perera"))
                .andExpect(jsonPath("$.businessName").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant WHERE auth_user_id = ?", Integer.class, nimal))
                .isEqualTo(1);
    }

    @Test
    void aSecondVisitDoesNotCreateAnotherProfile() throws Exception {
        mockMvc.perform(get("/api/merchants/me").with(merchant(nimal))).andExpect(status().isOk());
        mockMvc.perform(get("/api/merchants/me").with(merchant(nimal))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant WHERE auth_user_id = ?", Integer.class, nimal))
                .isEqualTo(1);
    }

    @Test
    void completingTheProfile() throws Exception {
        mockMvc.perform(put("/api/merchants/me").with(merchant(nimal))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPLETE_PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.businessName").value("Ceylon Tea House"));

        mockMvc.perform(get("/api/merchants/me").with(merchant(nimal)))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.city").value("Colombo"));
    }

    @Test
    void missingRequiredDetailsAreRefused() throws Exception {
        mockMvc.perform(put("/api/merchants/me").with(merchant(nimal))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"businessName\":\"Only a name\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void merchantSoftwareGets403NotACrash() throws Exception {
        mockMvc.perform(get("/api/merchants/me").with(jwt().jwt(token -> token.subject("mc_ceylontea"))
                        .authorities(new SimpleGrantedAuthority("merchant:api-access"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerCannotSeeMerchantProfiles() throws Exception {
        mockMvc.perform(get("/api/merchants/me").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("parcel:create"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void saveFlushAndReloadPersistedProfileData() throws Exception {
        UUID merchantId = UUID.randomUUID();
        mockMvc.perform(put("/api/merchants/me").with(merchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Ceylon Exports Ltd",
                                 "businessEmail":"contact@ceylonexports.com",
                                 "contactNumber":"+94112334455",
                                 "addressLine1":"45 Queen St",
                                 "addressLine2":"Floor 3",
                                 "city":"Kandy",
                                 "province":"Central",
                                 "postalCode":"20000",
                                 "country":"Sri Lanka"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true));

        entityManager.flush();
        entityManager.clear();

        Merchant persisted = merchantRepository.findByOwnerId(merchantId)
                .orElseThrow(() -> new AssertionError("Merchant profile was not persisted in database"));

        assertThat(persisted.getBusinessName()).isEqualTo("Ceylon Exports Ltd");
        assertThat(persisted.getBusinessEmail()).isEqualTo("contact@ceylonexports.com");
        assertThat(persisted.getContactNumber()).isEqualTo("+94112334455");
        assertThat(persisted.getAddressLine1()).isEqualTo("45 Queen St");
        assertThat(persisted.getAddressLine2()).isEqualTo("Floor 3");
        assertThat(persisted.getCity()).isEqualTo("Kandy");
        assertThat(persisted.getProvince()).isEqualTo("Central");
        assertThat(persisted.getPostalCode()).isEqualTo("20000");
        assertThat(persisted.getCountry()).isEqualTo("Sri Lanka");
        assertThat(persisted.isComplete()).isTrue();
    }

    @Test
    void editingACompletedProfileUpdatesValuesAndRetainsCompleteness() throws Exception {
        UUID merchantId = UUID.randomUUID();
        // Initial complete save
        mockMvc.perform(put("/api/merchants/me").with(merchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPLETE_PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.businessName").value("Ceylon Tea House"));

        // Subsequent edit on the completed profile
        String updatedJson = """
                {"businessName":"Ceylon Tea & Spice House",
                 "businessEmail":"updated@ceylontea.lk",
                 "contactNumber":"0119998888",
                 "addressLine1":"99 Marine Drive",
                 "addressLine2":"Suite 5A",
                 "city":"Colombo",
                 "province":"Western",
                 "postalCode":"00300",
                 "country":"Sri Lanka"}
                """;

        mockMvc.perform(put("/api/merchants/me").with(merchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON).content(updatedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.businessName").value("Ceylon Tea & Spice House"))
                .andExpect(jsonPath("$.businessEmail").value("updated@ceylontea.lk"))
                .andExpect(jsonPath("$.contactNumber").value("0119998888"))
                .andExpect(jsonPath("$.addressLine2").value("Suite 5A"))
                .andExpect(jsonPath("$.postalCode").value("00300"));

        // Verification through GET to confirm persistence and reload
        mockMvc.perform(get("/api/merchants/me").with(merchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.businessName").value("Ceylon Tea & Spice House"))
                .andExpect(jsonPath("$.contactNumber").value("0119998888"))
                .andExpect(jsonPath("$.addressLine1").value("99 Marine Drive"));
    }

    @Test
    void roleOnlyMerchantWithoutEntitlementIsDenied() throws Exception {
        UUID userId = UUID.randomUUID();
        // Token has role "MERCHANT", but lacks "merchant:api-access" authority
        RequestPostProcessor roleOnly = jwt().jwt(token -> token.subject(userId.toString())
                        .claim("name", "Unapproved Merchant")
                        .claim("roles", List.of("MERCHANT")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));

        mockMvc.perform(get("/api/merchants/me").with(roleOnly))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/merchants/me").with(roleOnly)
                        .contentType(MediaType.APPLICATION_JSON).content(COMPLETE_PROFILE))
                .andExpect(status().isForbidden());
    }

    @Test
    void separationOfTwoMerchantsProfiles() throws Exception {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();

        String profileA = """
                {"businessName":"Merchant Alpha Goods",
                 "businessEmail":"alpha@goods.lk",
                 "contactNumber":"0111111111",
                 "addressLine1":"10 Alpha Road",
                 "city":"Colombo",
                 "country":"Sri Lanka"}
                """;

        String profileB = """
                {"businessName":"Merchant Beta Logistics",
                 "businessEmail":"beta@logistics.lk",
                 "contactNumber":"0222222222",
                 "addressLine1":"20 Beta Lane",
                 "city":"Galle",
                 "country":"Sri Lanka"}
                """;

        // Merchant A saves profile
        mockMvc.perform(put("/api/merchants/me").with(merchant(merchantA))
                        .contentType(MediaType.APPLICATION_JSON).content(profileA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Merchant Alpha Goods"));

        // Merchant B saves profile
        mockMvc.perform(put("/api/merchants/me").with(merchant(merchantB))
                        .contentType(MediaType.APPLICATION_JSON).content(profileB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Merchant Beta Logistics"));

        // Verify Merchant A sees ONLY profile A
        mockMvc.perform(get("/api/merchants/me").with(merchant(merchantA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Merchant Alpha Goods"))
                .andExpect(jsonPath("$.city").value("Colombo"));

        // Verify Merchant B sees ONLY profile B
        mockMvc.perform(get("/api/merchants/me").with(merchant(merchantB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Merchant Beta Logistics"))
                .andExpect(jsonPath("$.city").value("Galle"));
    }
}
