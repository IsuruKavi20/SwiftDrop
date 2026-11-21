package com.swiftdrop.backend.dispatcher.controller;

import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Delivery Zones page's API (/api/admin/zones). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminZoneApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void onlyAdminsManageZones() throws Exception {
        mockMvc.perform(get("/api/admin/zones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/zones").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/zones").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content(zone("Western Metro", "WP", "\"Colombo\"", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsListsAndEditsZones() throws Exception {
        // Lower-case code and district names are tidied; districts come back in province order
        save(null, zone(" Western Metro ", "wp", "\"kalutara\", \"Colombo\", \"GAMPAHA\", \"Colombo\"", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Western Metro"))
                .andExpect(jsonPath("$.code").value("WP"))
                .andExpect(jsonPath("$.districts", contains("Colombo", "Gampaha", "Kalutara")))
                .andExpect(jsonPath("$.active").value(true));
        String central = save(null, zone("Central Hills", "CP", "\"Kandy\", \"Nuwara Eliya\"", false))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer centralId = JsonPath.read(central, "$.id");

        mockMvc.perform(get("/api/admin/zones").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("CP", "WP")))
                .andExpect(jsonPath("$[0].active").value(false));

        save(centralId, zone("Central Highlands", "CH", "\"Matale\", \"Kandy\"", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Central Highlands"))
                .andExpect(jsonPath("$.code").value("CH"))
                .andExpect(jsonPath("$.districts", contains("Kandy", "Matale")))
                .andExpect(jsonPath("$.active").value(true));
        // Nuwara Eliya was released, so another zone may take it
        save(null, zone("Hill Country", "HC", "\"Nuwara Eliya\"", true)).andExpect(status().isCreated());
        // Saving a zone with its own code and districts is not a clash
        save(centralId, zone("Central Highlands", "CH", "\"Kandy\", \"Matale\"", false)).andExpect(status().isOk());
    }

    @Test
    void refusesClashesAndUnknownValues() throws Exception {
        save(null, zone("Western Metro", "WP", "\"Colombo\", \"Gampaha\"", true)).andExpect(status().isCreated());

        save(null, zone("Western Two", "wp", "\"Kalutara\"", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Another zone already uses the code WP."));
        save(null, zone("Greater Colombo", "GC", "\"Gampaha\", \"Colombo\", \"Kalutara\"", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already in another zone: Colombo (WP), Gampaha (WP)."));
        save(null, zone("Atlantis", "AT", "\"Colombo North\"", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Not a Sri Lankan district: Colombo North."));
        save(null, zone("Bad code", "W1", "\"Kalutara\"", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("The code is 2 to 4 letters, e.g. WP"));
        save(null, "{\"name\":\"\",\"code\":\"KL\",\"districts\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Enter a zone name"))
                .andExpect(jsonPath("$.errors.districts").value("Choose at least one district"))
                .andExpect(jsonPath("$.errors.active").value("Say whether the zone is active"));
        save(999999, zone("Ghost", "GH", "\"Kalutara\"", true)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/zones").with(admin())).andExpect(jsonPath("$[*].code", contains("WP")));
    }

    private ResultActions save(Integer id, String body) throws Exception {
        var request = id == null ? post("/api/admin/zones") : put("/api/admin/zones/" + id);
        return mockMvc.perform(request.with(admin()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String zone(String name, String code, String districts, boolean active) {
        return "{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"districts\":[" + districts + "],\"active\":" + active + "}";
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
