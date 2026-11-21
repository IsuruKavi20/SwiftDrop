package com.swiftdrop.backend.dispatcher.controller;

import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.dispatcher.repository.CourierRepository;
import com.swiftdrop.backend.identity.client.DirectoryClient;
import com.swiftdrop.backend.identity.entity.AppUser;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creating and editing courier profiles (POST/PUT /api/dispatcher/couriers, GET /couriers/candidates). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourierProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierRepository couriers;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    /** The auth service: knows nobody beyond the people saved here. */
    @MockitoBean
    private DirectoryClient directory;

    private final UUID adminId = UUID.randomUUID();
    private AppUser nadeesha;
    private AppUser pathum;
    private AppUser alice;
    private Courier kasun;

    @BeforeEach
    void setUp() {
        when(directory.get(any(), anyBoolean())).thenReturn(Optional.empty());
        TestPeople.saved(em, adminId, "Amaya Admin", "ADMIN");
        nadeesha = TestPeople.saved(em, "Nadeesha Herath", "COURIER");
        pathum = TestPeople.saved(em, "Pathum Weerasinghe", "COURIER");
        alice = TestPeople.saved(em, "Alice Customer", "CUSTOMER");
        kasun = new Courier(TestPeople.saved(em, "Kasun Fernando", "COURIER"), "0771234567");
        kasun.setAvailability(CourierAvailability.AVAILABLE);
        em.persist(kasun);
        em.flush();
        em.clear();
    }

    @Test
    void onlyAdminsManageCouriers() throws Exception {
        mockMvc.perform(get("/api/dispatcher/couriers/candidates")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/dispatcher/couriers").with(customerToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body(nadeesha.getId(), "0771111111", "AVAILABLE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/dispatcher/couriers/" + kasun.getId()).with(customerToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body(null, "0771111111", "OFFLINE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidatesAreCourierAccountsWithoutAProfile() throws Exception {
        mockMvc.perform(get("/api/dispatcher/couriers/candidates").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Nadeesha Herath", "Pathum Weerasinghe")))
                .andExpect(jsonPath("$[0].userId").value(nadeesha.getId().toString()));
    }

    @Test
    void createsAProfileForACourierAccount() throws Exception {
        create(nadeesha.getId(), " 077 222 3333 ", "AVAILABLE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").isNumber())
                .andExpect(jsonPath("$.name").value("Nadeesha Herath"))
                .andExpect(jsonPath("$.userId").value(nadeesha.getId().toString()))
                .andExpect(jsonPath("$.phoneNumber").value("077 222 3333"))
                .andExpect(jsonPath("$.vehicleType").value("Motorbike"))
                .andExpect(jsonPath("$.vehicleNumber").doesNotExist())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.activeDeliveries").value(0))
                .andExpect(jsonPath("$.accountActive").value(true))
                .andExpect(jsonPath("$.courierRole").value(true));

        assertThat(couriers.findByUserId(nadeesha.getId())).isPresent();
        mockMvc.perform(get("/api/dispatcher/couriers/candidates").with(admin()))
                .andExpect(jsonPath("$[*].name", contains("Pathum Weerasinghe")));
        // Now offered in the assign dialog
        mockMvc.perform(get("/api/dispatcher/couriers/assignable").with(admin()))
                .andExpect(jsonPath("$[*].name", contains("Kasun Fernando", "Nadeesha Herath")));
    }

    @Test
    void refusesProfilesThatBreakTheRules() throws Exception {
        // A second profile for the same person
        create(kasun.getUser().getId(), "0771234567", "OFFLINE").andExpect(status().isConflict());
        // Not a courier
        create(alice.getId(), "0771234567", "OFFLINE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Alice Customer doesn't have the Courier role. Give it in Users and Roles first."));
        // Unknown to the auth service, or blocked
        create(UUID.randomUUID(), "0771234567", "OFFLINE").andExpect(status().isNotFound());
        em.createNativeQuery("update app_users set active = false where id = :id").setParameter("id", pathum.getId()).executeUpdate();
        em.clear();
        create(pathum.getId(), "0771234567", "OFFLINE").andExpect(status().isNotFound());
        // No account chosen, bad phone, too long a vehicle number, no availability
        create(null, "0771234567", "OFFLINE").andExpect(status().isBadRequest());
        create(nadeesha.getId(), "12ab", "OFFLINE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumber").value("Enter a phone number of 9 to 20 characters"));
        mockMvc.perform(post("/api/dispatcher/couriers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + nadeesha.getId() + "\",\"phoneNumber\":\"0771234567\",\"vehicleNumber\":\""
                                + "X".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vehicleNumber").exists())
                .andExpect(jsonPath("$.errors.availability").value("Choose an availability"));

        assertThat(couriers.findByUserId(nadeesha.getId())).isEmpty();
    }

    @Test
    void editsPhoneVehicleAndAvailability() throws Exception {
        mockMvc.perform(put("/api/dispatcher/couriers/" + kasun.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"0779998888\",\"vehicleType\":\"Van\",\"vehicleNumber\":\"WP KA-1234\","
                                + "\"availability\":\"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("0779998888"))
                .andExpect(jsonPath("$.vehicleType").value("Van"))
                .andExpect(jsonPath("$.vehicleNumber").value("WP KA-1234"))
                .andExpect(jsonPath("$.availability").value("OFFLINE"));

        Courier saved = couriers.findById(kasun.getId()).orElseThrow();
        assertThat(saved.getAvailability()).isEqualTo(CourierAvailability.OFFLINE);
        assertThat(saved.getVehicleType()).isEqualTo("Van");
        mockMvc.perform(get("/api/dispatcher/couriers/assignable").with(admin()))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/api/dispatcher/couriers/999999").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "0771234567", "OFFLINE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void goingOffDutyKeepsCurrentDeliveries() throws Exception {
        Customer customer = new Customer(em.find(AppUser.class, alice.getId()), "0771234567");
        em.persist(customer);
        Delivery delivery = new Delivery(customer, "SD-300001", UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Recipient", "0711234567", "1 Main Street",
                "Kandy", "Kandy", null));
        em.persist(delivery);
        Courier courier = em.find(Courier.class, kasun.getId());
        em.persist(new CourierAssignment(delivery, courier, em.find(AppUser.class, adminId), Instant.parse("2026-11-11T09:00:00Z")));
        delivery.assignCourier(courier.getUser());
        em.flush();
        em.clear();

        mockMvc.perform(put("/api/dispatcher/couriers/" + kasun.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "0771234567", "OFFLINE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDeliveries").value(1))
                .andExpect(jsonPath("$.currentTrackingNumbers", contains("SD-300001")));
    }

    @Test
    void onlyAUsableCourierAccountCanBeMadeAvailable() throws Exception {
        em.createNativeQuery("update courier set availability = 'OFFLINE' where courier_id = :id").setParameter("id", kasun.getId()).executeUpdate();
        em.createNativeQuery("update app_users set active = false where id = :id").setParameter("id", kasun.getUser().getId()).executeUpdate();
        em.clear();

        mockMvc.perform(put("/api/dispatcher/couriers/" + kasun.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "0771234567", "AVAILABLE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Kasun Fernando's account is blocked, so they can't be made available."));
        // Still allowed: edits that keep them off duty
        mockMvc.perform(put("/api/dispatcher/couriers/" + kasun.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "0770000000", "OFFLINE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountActive").value(false));
    }

    private ResultActions create(UUID userId, String phone, String availability) throws Exception {
        return mockMvc.perform(post("/api/dispatcher/couriers").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body(userId, phone, availability)));
    }

    private static String body(UUID userId, String phone, String availability) {
        String user = userId == null ? "" : "\"userId\":\"" + userId + "\",";
        return "{" + user + "\"phoneNumber\":\"" + phone + "\",\"vehicleType\":\"Motorbike\",\"vehicleNumber\":\"  \","
                + "\"availability\":\"" + availability + "\"}";
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject(adminId.toString()).claim("name", "Amaya Admin").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customerToken() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
