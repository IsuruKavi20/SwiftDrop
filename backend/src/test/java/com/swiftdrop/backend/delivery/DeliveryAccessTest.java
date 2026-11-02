package com.swiftdrop.backend.delivery;

import com.swiftdrop.backend.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may see which delivery. The tokens come from the SwiftDrop auth service; here they are simulated with the
 * same claims: "sub" = the user id, "roles" and "entitlements" (permissions) become Spring authorities.
 */
@WebMvcTest(DeliveryController.class)
@Import({SecurityConfig.class, DeliveryRepository.class})
class DeliveryAccessTest {

    private static final String COURIER_A = UUID.randomUUID().toString();
    private static final String COURIER_B = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryRepository deliveries;

    private Delivery deliveryOfA;

    @BeforeEach
    void oneDeliveryAssignedToCourierA() {
        deliveryOfA = deliveries.save(new Delivery(UUID.randomUUID(), "PARCEL-1", UUID.fromString(COURIER_A), "ASSIGNED"));
    }

    // Required by the brief
    @Test
    void aCourierCannotAccessAnotherCouriersDelivery() throws Exception {
        mockMvc.perform(get("/api/deliveries/{id}", deliveryOfA.id()).with(courier(COURIER_B)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCourierCanSeeTheirOwnDelivery() throws Exception {
        mockMvc.perform(get("/api/deliveries/{id}", deliveryOfA.id()).with(courier(COURIER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelReference").value("PARCEL-1"));
    }

    @Test
    void aCouriersListShowsOnlyTheirOwnDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries/mine").with(courier(COURIER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aDispatcherCanSeeEveryDelivery() throws Exception {
        mockMvc.perform(get("/api/deliveries/{id}", deliveryOfA.id()).with(dispatcher()))
                .andExpect(status().isOk());
    }

    @Test
    void aDispatcherCanListAllDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries").with(dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.parcelReference == 'PARCEL-1')]").exists());
    }

    @Test
    void aCourierCannotListAllDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries").with(courier(COURIER_A)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerCannotSeeDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries/{id}", deliveryOfA.id())
                        .with(user(UUID.randomUUID().toString(), "ROLE_CUSTOMER", "parcel:create", "parcel:read-own")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutATokenEverythingIsRefused() throws Exception {
        mockMvc.perform(get("/api/deliveries/{id}", deliveryOfA.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyADispatcherMayAssignADelivery() throws Exception {
        String body = "{\"parcelReference\":\"PARCEL-2\",\"courierId\":\"" + COURIER_A + "\"}";
        mockMvc.perform(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content(body).with(courier(COURIER_A)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content(body).with(dispatcher()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value(COURIER_A));
    }

    private static RequestPostProcessor courier(String userId) {
        return user(userId, "ROLE_COURIER", "delivery:read-own", "delivery:update-status");
    }

    private static RequestPostProcessor dispatcher() {
        return user(UUID.randomUUID().toString(), "ROLE_DISPATCHER", "parcel:read-all", "parcel:assign", "delivery:read-all");
    }

    private static RequestPostProcessor user(String userId, String... authorities) {
        return jwt().jwt(token -> token.subject(userId))
                .authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
    }
}
