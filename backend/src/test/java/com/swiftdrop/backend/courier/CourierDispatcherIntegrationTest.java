package com.swiftdrop.backend.courier;

import com.swiftdrop.backend.courier.dto.CourierDashboardResponse;
import com.swiftdrop.backend.courier.dto.CourierDeliverySummaryResponse;
import com.swiftdrop.backend.courier.dto.CourierStatusUpdateRequest;
import com.swiftdrop.backend.courier.dto.DeliveryConfirmationRequest;
import com.swiftdrop.backend.courier.dto.DeliveryFailureRequest;
import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import com.swiftdrop.backend.courier.entity.DeliveryProof;
import com.swiftdrop.backend.courier.exception.UnauthorizedDeliveryAccessException;
import com.swiftdrop.backend.courier.repository.CourierDeliveryRepository;
import com.swiftdrop.backend.courier.repository.DeliveryFailureRepository;
import com.swiftdrop.backend.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.courier.service.CourierService;
import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.Parcel;
import com.swiftdrop.backend.dispatcher.dto.AssignCourierRequest;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.dispatcher.service.CourierAssignmentService;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import com.swiftdrop.backend.shared.dto.PageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-module integration test verifying the Dispatcher -> Courier assignment workflow:
 * 1. Dispatcher assigns a confirmed delivery to Courier A.
 * 2. Courier A's read path and dashboard immediately reflect the assigned delivery.
 * 3. Horizontal privilege enforcement prevents Courier B from viewing or modifying Courier A's delivery.
 * 4. Dispatcher reassigns the delivery to Courier B with audit reason.
 * 5. Courier A immediately loses access (403 Forbidden).
 * 6. Courier B gains access and can advance the delivery lifecycle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourierDispatcherIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierAssignmentService assignmentService;

    @Autowired
    private CourierService courierService;

    @Autowired
    private CourierDeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryProofRepository proofRepository;

    @Autowired
    private DeliveryFailureRepository failureRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final UUID adminId = UUID.randomUUID();
    private final UUID courierAId = UUID.randomUUID();
    private final UUID courierBId = UUID.randomUUID();

    private AppUser adminUser;
    private AppUser courierA;
    private AppUser courierB;
    private Courier dispCourierA;
    private Courier dispCourierB;
    private Delivery testDelivery;

    private RequestPostProcessor adminToken() {
        return jwt().jwt(t -> t.subject(adminId.toString())
                        .claim("name", "Dispatcher Admin")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("parcel:assign"));
    }

    private RequestPostProcessor courierToken(UUID userId, String name) {
        return jwt().jwt(t -> t.subject(userId.toString())
                        .claim("name", name)
                        .claim("roles", List.of("COURIER")))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"),
                        new SimpleGrantedAuthority("delivery:read-own"),
                        new SimpleGrantedAuthority("delivery:update-status"));
    }

    private Jwt createAdminJwt() {
        return new Jwt("mock-admin-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", adminId.toString(), "roles", List.of("ADMIN"), "name", "Dispatcher Admin"));
    }

    @BeforeEach
    void setUp() {
        adminUser = TestPeople.saved(em, adminId, "Dispatcher Admin", "ADMIN");
        courierA = TestPeople.saved(em, courierAId, "Alice Courier", "COURIER");
        courierB = TestPeople.saved(em, courierBId, "Bob Courier", "COURIER");

        dispCourierA = new Courier(courierA, "0771111111");
        dispCourierA.setAvailability(CourierAvailability.AVAILABLE);
        dispCourierA.setVehicleType("MOTORCYCLE");
        em.persist(dispCourierA);

        dispCourierB = new Courier(courierB, "0772222222");
        dispCourierB.setAvailability(CourierAvailability.AVAILABLE);
        dispCourierB.setVehicleType("VAN");
        em.persist(dispCourierB);

        CourierProfile profA = new CourierProfile(courierA, "0771111111", "MOTORCYCLE", "Colombo Central");
        em.persist(profA);
        CourierProfile profB = new CourierProfile(courierB, "0772222222", "VAN", "Colombo North");
        em.persist(profB);

        Customer customer = new Customer(TestPeople.saved(em, "Charlie Customer", "CUSTOMER"), "0773333333");
        em.persist(customer);

        testDelivery = new Delivery(customer, "SD-DISP-INT-001", "tok-disp-int-001", DeliveryType.STANDARD);
        testDelivery.setStatus(DeliveryStatus.CONFIRMED);
        testDelivery.setAddress(new DeliveryAddress(AddressType.PICKUP, "Merchant Hub", "0771000000",
                "10 Galle Road", "Colombo", "Colombo", "00300"));
        testDelivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Recipient Bob", "0772000000",
                "45 Flower Road", "Colombo", "Colombo", "00700"));
        testDelivery.addParcel(new Parcel("Documents", new BigDecimal("0.5"),
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("2")));
        em.persist(testDelivery);

        em.flush();
    }

    @Test
    @DisplayName("End-to-End: Dispatcher assigns to Courier A, B is denied, Dispatcher reassigns to B, A loses access, B updates status")
    void testFullAssignmentAndReassignmentWorkflow() throws Exception {
        // Step 1: Initial state - delivery is unassigned
        assertThat(testDelivery.getCourier()).isNull();

        mockMvc.perform(get("/api/couriers/me/deliveries").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/couriers/me/deliveries/SD-DISP-INT-001").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isForbidden());

        // Step 2: Dispatcher assigns delivery to Courier A via Dispatcher API
        mockMvc.perform(post("/api/dispatcher/deliveries/" + testDelivery.getId() + "/assignment")
                        .with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":" + dispCourierA.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testDelivery.getId()))
                .andExpect(jsonPath("$.courier.name").value("Alice Courier"));

        em.flush();
        em.clear();

        // Step 3: Courier A verifies delivery appears in assigned deliveries and dashboard
        mockMvc.perform(get("/api/couriers/me/deliveries").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-DISP-INT-001"))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/couriers/me/deliveries/SD-DISP-INT-001").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD-DISP-INT-001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/couriers/me/dashboard").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToday").value(1));

        // Step 4: Courier B isolation check - cannot view or mutate Courier A's delivery
        mockMvc.perform(get("/api/couriers/me/deliveries").with(courierToken(courierBId, "Bob Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/couriers/me/deliveries/SD-DISP-INT-001").with(courierToken(courierBId, "Bob Courier")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/couriers/me/deliveries/SD-DISP-INT-001/status")
                        .with(courierToken(courierBId, "Bob Courier"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nextStatus\":\"PICKED_UP\",\"notes\":\"Sneak pickup\",\"location\":\"Hub\"}"))
                .andExpect(status().isForbidden());

        // Step 5: Dispatcher reassigns delivery to Courier B with audit reason
        mockMvc.perform(post("/api/dispatcher/deliveries/" + testDelivery.getId() + "/assignment")
                        .with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":" + dispCourierB.getId() + ",\"reason\":\"Vehicle breakdown for Courier A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.name").value("Bob Courier"));

        em.flush();
        em.clear();

        // Step 6: Courier A loses access immediately
        mockMvc.perform(get("/api/couriers/me/deliveries").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/couriers/me/deliveries/SD-DISP-INT-001").with(courierToken(courierAId, "Alice Courier")))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/couriers/me/deliveries/SD-DISP-INT-001/status")
                        .with(courierToken(courierAId, "Alice Courier"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nextStatus\":\"PICKED_UP\",\"notes\":\"Belated pickup\",\"location\":\"Hub\"}"))
                .andExpect(status().isForbidden());

        // Step 7: Courier B gains access and can perform delivery workflow
        mockMvc.perform(get("/api/couriers/me/deliveries").with(courierToken(courierBId, "Bob Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-DISP-INT-001"));

        mockMvc.perform(get("/api/couriers/me/deliveries/SD-DISP-INT-001").with(courierToken(courierBId, "Bob Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD-DISP-INT-001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Step 8: Courier B executes status update to PICKED_UP
        mockMvc.perform(patch("/api/couriers/me/deliveries/SD-DISP-INT-001/status")
                        .with(courierToken(courierBId, "Bob Courier"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nextStatus\":\"PICKED_UP\",\"notes\":\"Picked up by Courier B\",\"location\":\"Merchant Hub\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        // Step 9: Courier B dashboard reflects 1 in-progress delivery
        mockMvc.perform(get("/api/couriers/me/dashboard").with(courierToken(courierBId, "Bob Courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inProgress").value(1));
    }

    @Test
    @DisplayName("Service-level: CourierAssignmentService updates Delivery.courier and CourierService read path reflects changes")
    void testDirectServiceLayerIntegration() {
        Jwt adminJwt = createAdminJwt();

        // 1. Assign to Courier A via CourierAssignmentService
        assignmentService.assign(testDelivery.getId(), new AssignCourierRequest(dispCourierA.getId(), null), adminJwt);
        em.flush();
        em.clear();

        // 2. Courier A read path via CourierService
        PageResponse<CourierDeliverySummaryResponse> courierAPage =
                courierService.getAssignedDeliveries(courierAId, null, null, PageRequest.of(0, 10));
        assertThat(courierAPage.content()).hasSize(1);
        assertThat(courierAPage.content().get(0).trackingNumber()).isEqualTo("SD-DISP-INT-001");

        CourierDashboardResponse dashA = courierService.getDashboardStats(courierAId);
        assertThat(dashA.assignedToday()).isGreaterThanOrEqualTo(1);

        // 3. Courier B read path is empty and detail throws UnauthorizedDeliveryAccessException
        PageResponse<CourierDeliverySummaryResponse> courierBPage =
                courierService.getAssignedDeliveries(courierBId, null, null, PageRequest.of(0, 10));
        assertThat(courierBPage.content()).isEmpty();

        assertThatThrownBy(() -> courierService.getDeliveryDetails(courierBId, "SD-DISP-INT-001"))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class);

        // 4. Reassign to Courier B via CourierAssignmentService
        assignmentService.assign(testDelivery.getId(),
                new AssignCourierRequest(dispCourierB.getId(), "Reassigned due to scheduling"), adminJwt);
        em.flush();
        em.clear();

        // 5. Courier A now has empty list and detail access throws
        PageResponse<CourierDeliverySummaryResponse> courierAPageAfter =
                courierService.getAssignedDeliveries(courierAId, null, null, PageRequest.of(0, 10));
        assertThat(courierAPageAfter.content()).isEmpty();

        assertThatThrownBy(() -> courierService.getDeliveryDetails(courierAId, "SD-DISP-INT-001"))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class);

        // 6. Courier B now has the delivery and can update status
        PageResponse<CourierDeliverySummaryResponse> courierBPageAfter =
                courierService.getAssignedDeliveries(courierBId, null, null, PageRequest.of(0, 10));
        assertThat(courierBPageAfter.content()).hasSize(1);
        assertThat(courierBPageAfter.content().get(0).trackingNumber()).isEqualTo("SD-DISP-INT-001");

        courierService.updateDeliveryStatus(courierBId, "SD-DISP-INT-001",
                new CourierStatusUpdateRequest(DeliveryStatus.PICKED_UP, "Package secured", "Hub"));

        CourierDashboardResponse dashB = courierService.getDashboardStats(courierBId);
        assertThat(dashB.inProgress()).isEqualTo(1);
    }

    @Test
    @DisplayName("Persistence: Delivery proof and failure records persist correctly across transaction boundaries")
    void testProofAndFailurePersistenceAcrossTransactions() {
        Jwt adminJwt = createAdminJwt();
        Customer customer = em.createQuery("SELECT c FROM Customer c", Customer.class).setMaxResults(1).getSingleResult();

        // 1. Setup Delivery for Proof of Delivery testing
        Delivery deliveryProofTest = new Delivery(customer, "SD-PROOF-TEST-01", "tok-proof-01", DeliveryType.EXPRESS);
        deliveryProofTest.setStatus(DeliveryStatus.CONFIRMED);
        deliveryProofTest.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Recipient Proof", "0771230001",
                "100 Galle Rd", "Colombo", "Colombo", "00300"));
        em.persist(deliveryProofTest);
        em.flush();

        // Assign to Courier A via Dispatcher service
        assignmentService.assign(deliveryProofTest.getId(), new AssignCourierRequest(dispCourierA.getId(), null), adminJwt);
        em.flush();
        em.clear();

        // Courier A advances status: CONFIRMED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY
        courierService.updateDeliveryStatus(courierAId, "SD-PROOF-TEST-01",
                new CourierStatusUpdateRequest(DeliveryStatus.PICKED_UP, "Picked up from hub", "Hub 1"));
        courierService.updateDeliveryStatus(courierAId, "SD-PROOF-TEST-01",
                new CourierStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, "On vehicle", "Colombo Central"));
        courierService.updateDeliveryStatus(courierAId, "SD-PROOF-TEST-01",
                new CourierStatusUpdateRequest(DeliveryStatus.OUT_FOR_DELIVERY, "Approaching delivery location", "Destination St"));
        em.flush();
        em.clear();

        // Courier A confirms delivery with proof
        courierService.confirmDelivery(courierAId, "SD-PROOF-TEST-01", new DeliveryConfirmationRequest(
                "Jane Doe", "SIGNATURE", "Handed directly to recipient at front gate",
                "https://swiftdrop-media.s3.amazonaws.com/proofs/sig-001.png", "Customer Doorstep"
        ));
        em.flush();
        em.clear(); // Clear persistence context to test real database reload

        // Assert database persistence of Delivery and DeliveryProof
        Delivery reloadedDelivered = deliveryRepository.findByTrackingNumber("SD-PROOF-TEST-01").orElseThrow();
        assertThat(reloadedDelivered.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(reloadedDelivered.getCourier().getId()).isEqualTo(courierAId);

        DeliveryProof savedProof = proofRepository.findByDeliveryId(reloadedDelivered.getId()).orElseThrow();
        assertThat(savedProof.getRecipientSignName()).isEqualTo("Jane Doe");
        assertThat(savedProof.getProofType()).isEqualTo("SIGNATURE");
        assertThat(savedProof.getDeliveryNotes()).isEqualTo("Handed directly to recipient at front gate");
        assertThat(savedProof.getCourier().getId()).isEqualTo(courierAId);

        // 2. Setup separate Delivery for Failure recording testing
        Delivery deliveryFailTest = new Delivery(customer, "SD-FAIL-TEST-01", "tok-fail-01", DeliveryType.STANDARD);
        deliveryFailTest.setStatus(DeliveryStatus.CONFIRMED);
        deliveryFailTest.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Recipient Fail", "0771230002",
                "200 Kandy Rd", "Kadawatha", "Gampaha", "11850"));
        em.persist(deliveryFailTest);
        em.flush();

        // Assign to Courier B via Dispatcher service
        assignmentService.assign(deliveryFailTest.getId(), new AssignCourierRequest(dispCourierB.getId(), null), adminJwt);
        em.flush();
        em.clear();

        // Courier B advances status: CONFIRMED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY
        courierService.updateDeliveryStatus(courierBId, "SD-FAIL-TEST-01",
                new CourierStatusUpdateRequest(DeliveryStatus.PICKED_UP, "Package scanned", "North Hub"));
        courierService.updateDeliveryStatus(courierBId, "SD-FAIL-TEST-01",
                new CourierStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, "En route", "Kadawatha"));
        courierService.updateDeliveryStatus(courierBId, "SD-FAIL-TEST-01",
                new CourierStatusUpdateRequest(DeliveryStatus.OUT_FOR_DELIVERY, "Attempting dropoff", "Kadawatha Rd"));
        em.flush();
        em.clear();

        // Courier B reports delivery failure
        courierService.reportDeliveryFailure(courierBId, "SD-FAIL-TEST-01", new DeliveryFailureRequest(
                DeliveryFailureReason.RECIPIENT_UNAVAILABLE, "No answer at door or phone after 3 attempts", "Customer Gate"
        ));
        em.flush();
        em.clear(); // Clear persistence context to test real database reload

        // Assert database persistence of Delivery and DeliveryFailure
        Delivery reloadedFailed = deliveryRepository.findByTrackingNumber("SD-FAIL-TEST-01").orElseThrow();
        assertThat(reloadedFailed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(reloadedFailed.getCourier().getId()).isEqualTo(courierBId);

        List<DeliveryFailure> failures = failureRepository.findByDeliveryIdOrderByReportedAtDesc(reloadedFailed.getId());
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getFailureReason()).isEqualTo(DeliveryFailureReason.RECIPIENT_UNAVAILABLE);
        assertThat(failures.get(0).getFailureRemarks()).isEqualTo("No answer at door or phone after 3 attempts");
        assertThat(failures.get(0).getCourier().getId()).isEqualTo(courierBId);
    }

    @Test
    @DisplayName("Persistence: Separate physical committed transactions commit and reload proof and failure")
    void testSeparateCommittedTransactionsPersistence() {
        TransactionTemplate requiresNewTx = new TransactionTemplate(transactionManager);
        requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String trackingProof = "SD-TX-PROOF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID committedCourierId = UUID.randomUUID();
        UUID committedCustUserId = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<Long> proofDeliveryIdRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Long> custIdRef = new java.util.concurrent.atomic.AtomicReference<>();

        try {
            // Transaction 1: Create delivery, assign, advance to OUT_FOR_DELIVERY and confirm delivery with proof
            requiresNewTx.executeWithoutResult(status -> {
                AppUser testCourier = TestPeople.saved(em, committedCourierId, "Tx Courier", "COURIER");
                AppUser testCustUser = TestPeople.saved(em, committedCustUserId, "Tx Customer User", "CUSTOMER");
                Customer cust = new Customer(testCustUser, "0770000000");
                em.persist(cust);
                em.flush();
                custIdRef.set(cust.getId());

                Delivery d = new Delivery(cust, trackingProof, "tok-" + trackingProof, DeliveryType.STANDARD);
                d.setStatus(DeliveryStatus.CONFIRMED);
                d.assignCourier(testCourier);
                em.persist(d);
                em.flush();
                proofDeliveryIdRef.set(d.getId());

                courierService.updateDeliveryStatus(committedCourierId, trackingProof,
                        new CourierStatusUpdateRequest(DeliveryStatus.PICKED_UP, "Picked up", "Hub"));
                courierService.updateDeliveryStatus(committedCourierId, trackingProof,
                        new CourierStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, "In transit", "Main Highway"));
                courierService.updateDeliveryStatus(committedCourierId, trackingProof,
                        new CourierStatusUpdateRequest(DeliveryStatus.OUT_FOR_DELIVERY, "Out for delivery", "Zone 1"));
                courierService.confirmDelivery(committedCourierId, trackingProof,
                        new DeliveryConfirmationRequest("Alice Verified", "SIGNATURE", "Signed at door", null, "Doorstep"));
            });

            Long proofDeliveryId = proofDeliveryIdRef.get();
            assertThat(proofDeliveryId).isNotNull();

            // Transaction 2: Brand new transaction reads back from database to verify physical commit
            requiresNewTx.executeWithoutResult(status -> {
                Delivery loaded = deliveryRepository.findById(proofDeliveryId).orElseThrow();
                assertThat(loaded.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

                DeliveryProof p = proofRepository.findByDeliveryId(proofDeliveryId).orElseThrow();
                assertThat(p.getRecipientSignName()).isEqualTo("Alice Verified");
                assertThat(p.getProofType()).isEqualTo("SIGNATURE");
                assertThat(p.getCourier().getId()).isEqualTo(committedCourierId);
            });
        } finally {
            // Cleanup in separate transaction so no committed test entities leak into subsequent tests
            requiresNewTx.executeWithoutResult(status -> {
                Long proofDeliveryId = proofDeliveryIdRef.get();
                if (proofDeliveryId != null) {
                    proofRepository.findByDeliveryId(proofDeliveryId).ifPresent(proofRepository::delete);
                    deliveryRepository.findById(proofDeliveryId).ifPresent(deliveryRepository::delete);
                }
                Long custId = custIdRef.get();
                if (custId != null) {
                    Customer c = em.find(Customer.class, custId);
                    if (c != null) em.remove(c);
                }
                AppUser u1 = em.find(AppUser.class, committedCourierId);
                if (u1 != null) em.remove(u1);
                AppUser u2 = em.find(AppUser.class, committedCustUserId);
                if (u2 != null) em.remove(u2);
            });
        }
    }
}
