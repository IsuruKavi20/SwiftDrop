package com.swiftdrop.backend.courier.controller;

import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import com.swiftdrop.backend.courier.entity.DeliveryProof;
import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test verifying the complete Courier lifecycle workflow:
 * 1. Authentication and role verification
 * 2. Dashboard metrics retrieval
 * 3. Assigned deliveries list and pagination
 * 4. Delivery detail inspection (addresses, parcels, timeline)
 * 5. Incremental status advancement (CONFIRMED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY)
 * 6. Database persistence and tracking history audit log verification
 * 7. Delivery confirmation with recipient proof
 * 8. Dashboard recalculation upon completion
 * 9. Rejection of invalid status transitions (conflict)
 * 10. Rejection of cross-courier data access (forbidden)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourierWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    private AppUser courierA;
    private AppUser courierB;
    private Customer customer;
    private Delivery testDelivery;

    private static RequestPostProcessor courierToken(UUID userId, String name) {
        return jwt().jwt(token -> token.subject(userId.toString())
                        .claim("name", name)
                        .claim("roles", List.of("COURIER")))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"),
                        new SimpleGrantedAuthority("delivery:read-own"),
                        new SimpleGrantedAuthority("delivery:update-status"));
    }

    @BeforeEach
    void setUp() {
        courierA = TestPeople.saved(em, "Sachin Silva", "COURIER");
        courierB = TestPeople.saved(em, "Nimal Perera", "COURIER");
        AppUser customerUser = TestPeople.saved(em, "Ruwan Gamage", "CUSTOMER");

        CourierProfile profileA = new CourierProfile(courierA, "+94771234567", "MOTORCYCLE", "Colombo 03");
        em.persist(profileA);

        customer = new Customer(customerUser, "Ruwan", "Gamage", "ruwan@example.com", "0771239999");
        em.persist(customer);

        DeliveryAddress pickup = new DeliveryAddress(AddressType.PICKUP, "Sender Corp", "0112345678", "12 Galle Face Terrace", "Colombo", "Colombo", "00300");
        DeliveryAddress dest = new DeliveryAddress(AddressType.DESTINATION, "Recipient Anura", "0778889900", "88 Duplication Rd", "Colombo", "Colombo", "00400");

        testDelivery = new Delivery(customer, "SD-E2E-TEST-001", "tok-e2e-001", DeliveryType.EXPRESS);
        testDelivery.assignCourier(courierA);
        testDelivery.setAddress(pickup);
        testDelivery.setAddress(dest);
        testDelivery.addParcel(new Parcel("Express Electronics", new BigDecimal("1.2"), new BigDecimal("25"), new BigDecimal("15"), new BigDecimal("10")));
        testDelivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.CONFIRMED, "Delivery booked and confirmed", "Merchant Hub", Instant.now(), courierA));
        em.persist(testDelivery);

        em.flush();
    }

    @Test
    @DisplayName("Complete end-to-end delivery lifecycle workflow from pickup to proof of delivery")
    void executeCompleteCourierDeliveryWorkflow() throws Exception {
        String trackingNumber = testDelivery.getTrackingNumber();

        // -------------------------------------------------------------
        // Step 1: Courier opens Dashboard and retrieves initial statistics
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/couriers/me/dashboard")
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToday").isNumber())
                .andExpect(jsonPath("$.delivered").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        // -------------------------------------------------------------
        // Step 2: Courier retrieves assigned deliveries list
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/couriers/me/deliveries")
                        .param("status", "ALL")
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.content[0].recipientName").value("Recipient Anura"))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));

        // -------------------------------------------------------------
        // Step 3: Courier inspects delivery details
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/couriers/me/deliveries/" + trackingNumber)
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.deliveryAddress").value("88 Duplication Rd"))
                .andExpect(jsonPath("$.city").value("Colombo"))
                .andExpect(jsonPath("$.postalCode").value("00400"))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)));

        // -------------------------------------------------------------
        // Step 4: Advance status: CONFIRMED -> PICKED_UP
        // -------------------------------------------------------------
        mockMvc.perform(patch("/api/couriers/me/deliveries/" + trackingNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nextStatus": "PICKED_UP",
                                    "location": "Merchant Warehouse",
                                    "notes": "Parcel collected from merchant"
                                }
                                """)
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)));

        // -------------------------------------------------------------
        // Step 5: Advance status: PICKED_UP -> IN_TRANSIT
        // -------------------------------------------------------------
        mockMvc.perform(patch("/api/couriers/me/deliveries/" + trackingNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nextStatus": "IN_TRANSIT",
                                    "location": "Colombo Sorting Hub",
                                    "notes": "Dispatched on courier motorbike"
                                }
                                """)
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.statusHistory", hasSize(3)));

        // -------------------------------------------------------------
        // Step 6: Advance status: IN_TRANSIT -> OUT_FOR_DELIVERY
        // -------------------------------------------------------------
        mockMvc.perform(patch("/api/couriers/me/deliveries/" + trackingNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nextStatus": "OUT_FOR_DELIVERY",
                                    "location": "Duplication Road Zone",
                                    "notes": "Arrived at recipient destination"
                                }
                                """)
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.statusHistory", hasSize(4)));

        // -------------------------------------------------------------
        // Step 7: Confirm successful delivery with recipient signature
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/couriers/me/deliveries/" + trackingNumber + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientSignName": "Anura Kumara",
                                    "proofType": "SIGNATURE",
                                    "deliveryNotes": "Handed directly to recipient at doorstep",
                                    "proofMediaUrl": "https://storage.swiftdrop.com/proofs/anura-sig.jpg",
                                    "location": "88 Duplication Rd"
                                }
                                """)
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.proof.recipientSignName").value("Anura Kumara"))
                .andExpect(jsonPath("$.proof.deliveryNotes").value("Handed directly to recipient at doorstep"))
                .andExpect(jsonPath("$.statusHistory", hasSize(5)));

        // -------------------------------------------------------------
        // Step 8: Verify database persistence in JPA repository
        // -------------------------------------------------------------
        em.flush();
        em.clear();

        Delivery reloaded = em.createQuery("SELECT d FROM Delivery d WHERE d.trackingNumber = :tn", Delivery.class)
                .setParameter("tn", trackingNumber)
                .getSingleResult();
        assertEquals(DeliveryStatus.DELIVERED, reloaded.getStatus());
        assertEquals(5, reloaded.getTrackingEvents().size());

        DeliveryProof persistedProof = em.createQuery("SELECT p FROM DeliveryProof p WHERE p.delivery.id = :id", DeliveryProof.class)
                .setParameter("id", reloaded.getId())
                .getSingleResult();
        assertNotNull(persistedProof);
        assertEquals("Anura Kumara", persistedProof.getRecipientSignName());
        assertEquals("SIGNATURE", persistedProof.getProofType());

        // -------------------------------------------------------------
        // Step 9: Re-query dashboard to verify updated statistics
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/couriers/me/dashboard")
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(1))
                .andExpect(jsonPath("$.inProgress").value(0))
                .andExpect(jsonPath("$.completionPercentage").value(100));

        // -------------------------------------------------------------
        // Step 10: Verify terminal state rejects further transitions
        // -------------------------------------------------------------
        mockMvc.perform(patch("/api/couriers/me/deliveries/" + trackingNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nextStatus": "IN_TRANSIT",
                                    "notes": "Attempting transition from terminal DELIVERED status"
                                }
                                """)
                        .with(courierToken(courierA.getId(), "Sachin Silva")))
                .andExpect(status().isConflict());

        // -------------------------------------------------------------
        // Step 11: Verify cross-courier access is rejected
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/couriers/me/deliveries/" + trackingNumber)
                        .with(courierToken(courierB.getId(), "Nimal Perera")))
                .andExpect(status().isForbidden());
    }
}
