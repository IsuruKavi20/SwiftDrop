package com.swiftdrop.backend.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    private final UUID userId = UUID.randomUUID();

    private static RequestPostProcessor customer(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString())
                        .claim("email", "customer@test.com")
                        .claim("name", "Test Customer"))
                .authorities(new SimpleGrantedAuthority("parcel:read-own"), new SimpleGrantedAuthority("parcel:create"));
    }

    private static RequestPostProcessor unauthorized(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("other:permission"));
    }

    @Test
    void canFetchEmptyNotifications() throws Exception {
        mockMvc.perform(get("/api/customers/me/notifications")
                        .with(customer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void canFetchNotificationCount() throws Exception {
        mockMvc.perform(get("/api/customers/me/notifications/count")
                        .with(customer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void canTriggerAndRetrieveTestNotification() throws Exception {
        // Trigger test notification
        mockMvc.perform(post("/api/customers/me/notifications/test-trigger")
                        .param("trackingNumber", "SD-TEST-999")
                        .param("status", "OUT_FOR_DELIVERY")
                        .with(customer(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.channel").value("IN_APP"));

        // Verify count is now at least 1
        mockMvc.perform(get("/api/customers/me/notifications/count")
                        .with(customer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // Verify list returns the triggered notification
        mockMvc.perform(get("/api/customers/me/notifications")
                        .with(customer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unauthorizedCallerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/customers/me/notifications")
                        .with(unauthorized(userId)))
                .andExpect(status().isForbidden());
    }
}

