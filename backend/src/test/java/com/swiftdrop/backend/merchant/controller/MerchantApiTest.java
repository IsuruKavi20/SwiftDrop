package com.swiftdrop.backend.merchant.controller;

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
}
