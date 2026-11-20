package com.swiftdrop.backend.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every error of the API has the same JSON shape (RFC 9457): type, title, status, detail, code, requestId.
 * Covers the ones decided by security (401, 403), by our code (404, 400) and by Spring itself (405).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorFormatTest {

    private static final String UUID_PATTERN = "[0-9a-f-]{36}";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("parcel:create"), new SimpleGrantedAuthority("parcel:read-own"));
    }

    private static RequestPostProcessor courier() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("delivery:read-own"));
    }

    @Test
    void noTokenGives401WithABodyAndTheBearerHeader() throws Exception {
        mockMvc.perform(get("/api/customers/me/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.type").value("https://swiftdrop.dev/problems/unauthenticated"))
                .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)));
    }

    @Test
    void aMissingPermissionGives403WithABody() throws Exception {
        mockMvc.perform(get("/api/customers/me/dashboard").with(courier()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to do this."));
    }

    @Test
    void notFoundKeepsTheModulesMessageAndAddsTheCode() throws Exception {
        mockMvc.perform(get("/api/customers/me/dashboard").with(customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Customer profile not found. Please complete registration first."));
    }

    @Test
    void invalidFormListsEachField() throws Exception {
        mockMvc.perform(post("/api/customers/me").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.address").exists());
    }

    @Test
    void springsOwnErrorsGetTheSameShape() throws Exception {
        mockMvc.perform(delete("/api/customers/me/dashboard").with(customer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void aSafeRequestIdFromTheProxyIsKeptAndAnUnsafeOneReplaced() throws Exception {
        mockMvc.perform(get("/api/customers/me/dashboard").header(RequestIdFilter.HEADER, "proxy-abc-12345"))
                .andExpect(header().string(RequestIdFilter.HEADER, "proxy-abc-12345"))
                .andExpect(jsonPath("$.requestId").value("proxy-abc-12345"));
        mockMvc.perform(get("/api/customers/me/dashboard").header(RequestIdFilter.HEADER, "bad\nid injected into logs"))
                .andExpect(header().string(RequestIdFilter.HEADER, not(containsString("injected"))))
                .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)));
    }
}
