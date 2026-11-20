package com.swiftdrop.backend.courier.controller;

import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import com.swiftdrop.backend.courier.service.CourierService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    private AppUser courier1;
    private AppUser courier2;
    private AppUser customerUser;
    private AppUser dispatcherUser;
    private Customer customer;
    private Delivery delivery1;

    private static RequestPostProcessor courierToken(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", "Test Courier")
                        .claim("roles", List.of("COURIER")))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"),
                        new SimpleGrantedAuthority("delivery:read-own"),
                        new SimpleGrantedAuthority("delivery:update-status"));
    }

    private static RequestPostProcessor customerToken(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", "Test Customer")
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("parcel:read-own"),
                        new SimpleGrantedAuthority("parcel:create"));
    }

    private static RequestPostProcessor dispatcherToken(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", "Test Dispatcher")
                        .claim("roles", List.of("DISPATCHER")))
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"),
                        new SimpleGrantedAuthority("delivery:assign"),
                        new SimpleGrantedAuthority("courier:manage"));
    }

    @BeforeEach
    void setUp() {
        courier1 = TestPeople.saved(em, "Sachin Silva", "COURIER");
        courier2 = TestPeople.saved(em, "Nimal Perera", "COURIER");
        customerUser = TestPeople.saved(em, "Alice Fernando", "CUSTOMER");
        dispatcherUser = TestPeople.saved(em, "Dave Dispatcher", "DISPATCHER");

        CourierProfile profile1 = new CourierProfile(courier1, "+94771234567", "MOTORCYCLE", "Colombo Central");
        em.persist(profile1);

        customer = new Customer(customerUser, "Alice", "Fernando", "alice@example.com", "0771234567");
        em.persist(customer);

        DeliveryAddress pickup = new DeliveryAddress(AddressType.PICKUP, "Sender Alice", "0771111111", "10 Galle Rd", "Colombo", "Colombo", "00300");
        DeliveryAddress dest = new DeliveryAddress(AddressType.DESTINATION, "Recipient Bob", "0772222222", "45 Havelock Rd", "Colombo", "Colombo", "00500");

        delivery1 = new Delivery(customer, "SD-20261108-001", "tok-001", DeliveryType.EXPRESS);
        delivery1.assignCourier(courier1);
        delivery1.setAddress(pickup);
        delivery1.setAddress(dest);
        delivery1.addParcel(new Parcel("Documents", new BigDecimal("0.5"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("2")));
        delivery1.addTrackingEvent(new TrackingEvent(DeliveryStatus.PICKED_UP, "Package picked up by courier", "Colombo Hub", Instant.now(), courier1));
        em.persist(delivery1);

        em.flush();
    }

    @Test
    @DisplayName("1. Unauthenticated request is rejected with 401 Unauthorized")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/courier/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. Non-courier access is rejected with 403 Forbidden")
    void nonCourierAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/courier/dashboard").with(customerToken(customerUser.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Valid courier retrieves dashboard statistics successfully")
    void validCourierDashboardAccess() throws Exception {
        mockMvc.perform(get("/api/courier/dashboard").with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToday").isNumber())
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.delivered").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    @DisplayName("4. Courier retrieves operational profile")
    void validCourierProfileAccess() throws Exception {
        mockMvc.perform(get("/api/courier/profile").with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.assignedZone").value("Colombo Central"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("5. Courier retrieves assigned deliveries list with pagination")
    void courierOwnedDeliveriesListRetrieval() throws Exception {
        mockMvc.perform(get("/api/courier/deliveries")
                        .param("page", "0")
                        .param("size", "10")
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-20261108-001"))
                .andExpect(jsonPath("$.content[0].recipientName").value("Recipient Bob"))
                .andExpect(jsonPath("$.content[0].status").value("PICKED_UP"));
    }

    @Test
    @DisplayName("6. Courier retrieves individual delivery details")
    void courierDeliveryDetailRetrieval() throws Exception {
        mockMvc.perform(get("/api/courier/deliveries/" + delivery1.getTrackingNumber())
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD-20261108-001"))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)));
    }

    @Test
    @DisplayName("7. Cross-courier delivery detail access is rejected with 403 Forbidden")
    void crossCourierDeliveryAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/courier/deliveries/" + delivery1.getTrackingNumber())
                        .with(courierToken(courier2.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("not authorized")));
    }

    @Test
    @DisplayName("8. Cross-courier status update is rejected with 403 Forbidden")
    void crossCourierStatusUpdateIsRejected() throws Exception {
        String payload = """
                {
                    "nextStatus": "IN_TRANSIT",
                    "location": "Colombo 04",
                    "notes": "Attempting unauthorized status update"
                }
                """;

        mockMvc.perform(patch("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(courierToken(courier2.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Valid delivery status transition (PICKED_UP -> IN_TRANSIT)")
    void validDeliveryStatusUpdate() throws Exception {
        String payload = """
                {
                    "nextStatus": "IN_TRANSIT",
                    "location": "Bambalapitiya Junction",
                    "notes": "On courier motorcycle"
                }
                """;

        mockMvc.perform(patch("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)));
    }

    @Test
    @DisplayName("10. Invalid status transition is rejected with 409 Conflict")
    void invalidStatusTransitionIsRejected() throws Exception {
        // Direct transition from PICKED_UP to DELIVERED is not allowed
        String payload = """
                {
                    "nextStatus": "DELIVERED",
                    "location": "Destination",
                    "notes": "Skipping intermediate transit"
                }
                """;

        mockMvc.perform(patch("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("11. Successful delivery confirmation with proof of delivery")
    void successfulDeliveryConfirmation() throws Exception {
        // Advance to OUT_FOR_DELIVERY first
        delivery1.addTrackingEvent(new TrackingEvent(DeliveryStatus.IN_TRANSIT, "In transit", "Colombo Hub", Instant.now().minusSeconds(120), courier1));
        delivery1.addTrackingEvent(new TrackingEvent(DeliveryStatus.OUT_FOR_DELIVERY, "Out for delivery", "Colombo 05", Instant.now(), courier1));
        em.flush();

        String payload = """
                {
                    "recipientSignName": "Recipient Bob",
                    "proofType": "RECIPIENT_CONFIRMATION",
                    "deliveryNotes": "Signed and received at gate",
                    "proofMediaUrl": "https://storage.swiftdrop.com/proofs/test.jpg",
                    "location": "45 Havelock Rd"
                }
                """;

        mockMvc.perform(post("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.proof.recipientSignName").value("Recipient Bob"))
                .andExpect(jsonPath("$.proof.deliveryNotes").value("Signed and received at gate"));
    }

    @Test
    @DisplayName("12. Delivery failure reporting with reason and remarks")
    void deliveryFailureReporting() throws Exception {
        // Advance to OUT_FOR_DELIVERY first
        delivery1.addTrackingEvent(new TrackingEvent(DeliveryStatus.IN_TRANSIT, "In transit", "Colombo Hub", Instant.now().minusSeconds(120), courier1));
        delivery1.addTrackingEvent(new TrackingEvent(DeliveryStatus.OUT_FOR_DELIVERY, "Out for delivery", "Colombo 05", Instant.now(), courier1));
        em.flush();

        String payload = """
                {
                    "failureReason": "RECIPIENT_UNAVAILABLE",
                    "failureRemarks": "Gate locked, customer phone unanswered after 3 attempts",
                    "location": "45 Havelock Rd"
                }
                """;

        mockMvc.perform(post("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.latestFailure.failureReason").value("RECIPIENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.latestFailure.failureRemarks").value("Gate locked, customer phone unanswered after 3 attempts"));
    }

    @Test
    @DisplayName("13. Missing delivery handling returns 404 Not Found")
    void missingDeliveryHandling() throws Exception {
        mockMvc.perform(get("/api/courier/deliveries/NON-EXISTENT-TRACKING-999")
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Delivery not found with tracking number: NON-EXISTENT-TRACKING-999"));
    }

    @Test
    @DisplayName("14. Request validation errors return 400 Bad Request with field errors")
    void requestValidationErrorsReturnBadRequest() throws Exception {
        // Empty payload for status update
        mockMvc.perform(patch("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nextStatus").value("Next status is required"));

        // Blank recipientSignName for delivery confirmation
        mockMvc.perform(post("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientSignName\":\"\"}")
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.recipientSignName").exists());

        // Missing failureReason for delivery failure
        mockMvc.perform(post("/api/courier/deliveries/" + delivery1.getTrackingNumber() + "/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureRemarks\":\"\"}")
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.failureReason").value("Failure reason is required"));
    }

    @Test
    @DisplayName("15. Both path conventions (/api/courier and /api/couriers/me) are supported")
    void bothPathConventionsAreSupported() throws Exception {
        mockMvc.perform(get("/api/couriers/me/dashboard").with(courierToken(courier1.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/courier/dashboard").with(courierToken(courier1.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("16. Courier self-assigning operational zone or active status is rejected with 403 Forbidden; phone update succeeds")
    void courierSelfAssigningZoneIsRejected() throws Exception {
        // Attempting to self-register an operational zone as a courier
        String unauthorizedZonePayload = """
                {
                    "phoneNumber": "+94779998877",
                    "vehicleType": "VAN",
                    "assignedZone": "Kandy South",
                    "active": true
                }
                """;

        mockMvc.perform(post("/api/couriers/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unauthorizedZonePayload)
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("cannot self-assign operational delivery zones")));

        // Updating only personal phone number succeeds
        String validSelfUpdatePayload = """
                {
                    "phoneNumber": "+94770003344",
                    "vehicleType": "MOTORCYCLE",
                    "assignedZone": "Colombo Central"
                }
                """;

        mockMvc.perform(post("/api/couriers/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSelfUpdatePayload)
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+94770003344"));
    }

    @Test
    @DisplayName("17. Dispatcher provisions courier operational profile (vehicle, zone, active) successfully")
    void dispatcherProvisionsCourierProfile() throws Exception {
        AppUser newCourier = TestPeople.saved(em, "Sunil Perera", "COURIER");
        em.flush();

        String payload = """
                {
                    "phoneNumber": "+94779998877",
                    "vehicleType": "VAN",
                    "assignedZone": "Kandy South",
                    "active": true
                }
                """;

        mockMvc.perform(post("/api/courier/" + newCourier.getId() + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(dispatcherToken(dispatcherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+94779998877"))
                .andExpect(jsonPath("$.vehicleType").value("VAN"))
                .andExpect(jsonPath("$.assignedZone").value("Kandy South"))
                .andExpect(jsonPath("$.isActive").value(true));

        // Re-provisioning by dispatcher updates without duplicate records
        mockMvc.perform(post("/api/courier/" + newCourier.getId() + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(dispatcherToken(dispatcherUser.getId())))
                .andExpect(status().isOk());

        Long count = em.createQuery("SELECT count(c) FROM CourierProfile c WHERE c.user.id = :id", Long.class)
                .setParameter("id", newCourier.getId())
                .getSingleResult();
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    @Test
    @DisplayName("18. Inactive user or non-courier user cannot be provisioned as courier")
    void invalidUserProvisioningIsRejected() throws Exception {
        // Non-courier role
        String payload = """
                {
                    "phoneNumber": "+94771112233",
                    "vehicleType": "BICYCLE",
                    "assignedZone": "Galle"
                }
                """;

        mockMvc.perform(post("/api/courier/" + customerUser.getId() + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(dispatcherToken(dispatcherUser.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Only users with the COURIER role")));
    }

    @Test
    @DisplayName("19. Courier or Customer attempting to enumerate eligible couriers is rejected with 403 Forbidden")
    void unauthorizedCourierEnumerationIsRejected() throws Exception {
        // Courier caller rejected
        mockMvc.perform(get("/api/courier/eligible")
                        .with(courierToken(courier1.getId())))
                .andExpect(status().isForbidden());

        // Customer caller rejected
        mockMvc.perform(get("/api/courier/eligible")
                        .with(customerToken(customerUser.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("20. Dispatcher queries eligible couriers and filters by zone")
    void dispatcherQueriesEligibleCouriersFilteredByZone() throws Exception {
        mockMvc.perform(get("/api/courier/eligible")
                        .with(dispatcherToken(dispatcherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/courier/eligible")
                        .param("zone", "Colombo Central")
                        .with(dispatcherToken(dispatcherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].assignedZone").value("Colombo Central"));

        // Non-existent zone
        mockMvc.perform(get("/api/courier/eligible")
                        .param("zone", "NonExistentZone")
                        .with(dispatcherToken(dispatcherUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}
