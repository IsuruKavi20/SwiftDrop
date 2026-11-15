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
import com.swiftdrop.backend.dispatcher.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DispatcherApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierAssignmentRepository assignments;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final UUID adminId = UUID.randomUUID();
    private Courier kasun;
    private Courier ravi;
    private Courier chamara;
    private Delivery waiting;
    private Delivery olderWaiting;
    private Delivery inTransit;
    private Delivery delivered;
    private Delivery pending;

    @BeforeEach
    void setUp() {
        TestPeople.saved(em, adminId, "Amaya Admin", "ADMIN");
        kasun = courier("Kasun Fernando", CourierAvailability.AVAILABLE);
        ravi = courier("Ravi Silva", CourierAvailability.AVAILABLE);
        chamara = courier("Chamara Bandara", CourierAvailability.BUSY);
        Customer alice = new Customer(TestPeople.saved(em, "Alice Customer", "CUSTOMER"), "0771234567");
        em.persist(alice);

        olderWaiting = delivery(alice, "SD-200001", "Kandy", DeliveryStatus.CONFIRMED);
        waiting = delivery(alice, "SD-200002", "Galle", DeliveryStatus.CONFIRMED);
        inTransit = delivery(alice, "SD-200003", "Matara", DeliveryStatus.IN_TRANSIT);
        delivered = delivery(alice, "SD-200004", "Kandy", DeliveryStatus.DELIVERED);
        pending = delivery(alice, "SD-200005", "Jaffna", DeliveryStatus.PENDING);
        em.flush();
        em.createNativeQuery("update delivery set created_at = TIMESTAMP '2026-11-10 08:00:00' where tracking_number = 'SD-200001'").executeUpdate();
        em.createNativeQuery("update delivery set created_at = TIMESTAMP '2026-11-11 08:00:00' where tracking_number <> 'SD-200001'").executeUpdate();

        // In transit with Kasun: an active assignment and the delivery's courier
        AppUser assigner = em.find(AppUser.class, adminId);
        em.persist(new CourierAssignment(inTransit, kasun, assigner, java.time.Instant.parse("2026-11-11T09:00:00Z")));
        inTransit.assignCourier(kasun.getUser());
        em.flush();
        em.clear();
    }

    @Test
    void onlyAdminsMayUseTheDispatchApi() throws Exception {
        mockMvc.perform(get("/api/dispatcher/deliveries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dispatcher/deliveries").with(customerToken())).andExpect(status().isForbidden());
        // DISPATCHER carries parcel:assign, but the admin area is for admins
        mockMvc.perform(get("/api/dispatcher/deliveries").with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"), new SimpleGrantedAuthority("parcel:assign"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dispatcher/deliveries/" + waiting.getId() + "/assignment").with(customerToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":" + kasun.getId() + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dispatcher/deliveries").with(admin())).andExpect(status().isOk());
    }

    @Test
    void listsAndSearchesEveryDeliveryWithItsCourier() throws Exception {
        mockMvc.perform(get("/api/dispatcher/deliveries").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].source").value("CUSTOMER"));
        mockMvc.perform(get("/api/dispatcher/deliveries?status=IN_TRANSIT").with(admin()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SD-200003"))
                .andExpect(jsonPath("$.content[0].courier.courierId").value(kasun.getId()))
                .andExpect(jsonPath("$.content[0].courier.name").value("Kasun Fernando"))
                .andExpect(jsonPath("$.content[0].destination.city").value("Matara"));
        mockMvc.perform(get("/api/dispatcher/deliveries?search=kasun").with(admin()))
                .andExpect(jsonPath("$.content[*].trackingNumber", contains("SD-200003")));
        mockMvc.perform(get("/api/dispatcher/deliveries?size=2&page=1").with(admin()))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void unassignedListsConfirmedDeliveriesWithoutCourierOldestFirst() throws Exception {
        mockMvc.perform(get("/api/dispatcher/deliveries/unassigned").with(admin()))
                .andExpect(jsonPath("$.content[*].trackingNumber", contains("SD-200001", "SD-200002")))
                .andExpect(jsonPath("$.content[0].courier").doesNotExist());
        mockMvc.perform(get("/api/dispatcher/deliveries/unassigned?search=galle").with(admin()))
                .andExpect(jsonPath("$.content[*].trackingNumber", contains("SD-200002")));
    }

    @Test
    void countsEveryStatusAndTheUnassigned() throws Exception {
        mockMvc.perform(get("/api/dispatcher/deliveries/counts").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses.CONFIRMED").value(2))
                .andExpect(jsonPath("$.statuses.IN_TRANSIT").value(1))
                .andExpect(jsonPath("$.statuses.CANCELLED").value(0))
                .andExpect(jsonPath("$.unassigned").value(2));
    }

    @Test
    void assignsACourierAndShowsItInTheHistory() throws Exception {
        assign(waiting, kasun, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD-200002"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.courier.name").value("Kasun Fernando"))
                .andExpect(jsonPath("$.assignments", hasSize(1)))
                .andExpect(jsonPath("$.assignments[0].active").value(true))
                .andExpect(jsonPath("$.assignments[0].assignedByName").value("Amaya Admin"));

        mockMvc.perform(get("/api/dispatcher/deliveries/unassigned").with(admin()))
                .andExpect(jsonPath("$.content[*].trackingNumber", contains("SD-200001")));
        mockMvc.perform(get("/api/dispatcher/couriers").with(admin()))
                .andExpect(jsonPath("$[0].name").value("Kasun Fernando"))
                .andExpect(jsonPath("$[0].activeDeliveries").value(2))
                .andExpect(jsonPath("$[0].currentTrackingNumbers", contains("SD-200003", "SD-200002")));
    }

    @Test
    void reassigningNeedsAReasonAndKeepsTheEndedAssignment() throws Exception {
        assign(inTransit, ravi, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Give a reason for the reassignment."));

        assign(inTransit, ravi, "Vehicle breakdown")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.courierId").value(ravi.getId()))
                .andExpect(jsonPath("$.assignments", hasSize(2)))
                .andExpect(jsonPath("$.assignments[0].courierName").value("Ravi Silva"))
                .andExpect(jsonPath("$.assignments[0].active").value(true))
                .andExpect(jsonPath("$.assignments[1].courierName").value("Kasun Fernando"))
                .andExpect(jsonPath("$.assignments[1].active").value(false))
                .andExpect(jsonPath("$.assignments[1].endedByName").value("Amaya Admin"))
                .andExpect(jsonPath("$.assignments[1].endReason").value("Vehicle breakdown"));

        assertThat(assignments.findByDeliveryIdAndActiveTrue(inTransit.getId())).get()
                .extracting(a -> a.getCourier().getId()).isEqualTo(ravi.getId());
        assertThat(assignments.findByDeliveryIdOrderByAssignedAtDescIdDesc(inTransit.getId(), Pageable.unpaged())).hasSize(2);
        assertThat(em.find(Delivery.class, inTransit.getId()).getCourier().getId()).isEqualTo(ravi.getUser().getId());
    }

    @Test
    void refusesAssignmentsThatBreakTheRules() throws Exception {
        assign(inTransit, kasun, "again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Kasun Fernando is already assigned to this delivery."));
        assign(waiting, chamara, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Chamara Bandara is not available right now."));
        assign(delivered, kasun, null).andExpect(status().isConflict());
        assign(pending, kasun, null).andExpect(status().isConflict());
        mockMvc.perform(post("/api/dispatcher/deliveries/" + waiting.getId() + "/assignment").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":999999}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/dispatcher/deliveries/999999/assignment").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":" + kasun.getId() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/dispatcher/deliveries/" + waiting.getId() + "/assignment").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courierId").value("Choose a courier"));
        assertThat(assignments.findByDeliveryIdAndActiveTrue(waiting.getId())).isEmpty();
    }

    @Test
    void detailsIncludeAssignmentHistoryAndUnknownDeliveriesAre404() throws Exception {
        mockMvc.perform(get("/api/dispatcher/deliveries/" + inTransit.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inTransit.getId()))
                .andExpect(jsonPath("$.assignments[0].courierName").value("Kasun Fernando"));
        mockMvc.perform(get("/api/dispatcher/deliveries/999999").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void assignableCouriersAreAvailableAndLightestFirst() throws Exception {
        mockMvc.perform(get("/api/dispatcher/couriers/assignable").with(admin()))
                .andExpect(jsonPath("$[*].name", contains("Ravi Silva", "Kasun Fernando")));
        mockMvc.perform(get("/api/dispatcher/couriers?availability=BUSY").with(admin()))
                .andExpect(jsonPath("$[*].name", contains("Chamara Bandara")));
    }

    private ResultActions assign(Delivery delivery, Courier courier, String reason) throws Exception {
        String body = reason == null
                ? "{\"courierId\":" + courier.getId() + "}"
                : "{\"courierId\":" + courier.getId() + ",\"reason\":\"" + reason + "\"}";
        return mockMvc.perform(post("/api/dispatcher/deliveries/" + delivery.getId() + "/assignment").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject(adminId.toString()).claim("name", "Amaya Admin").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("parcel:assign"));
    }

    private static RequestPostProcessor customerToken() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("parcel:read-own"));
    }

    private Courier courier(String name, CourierAvailability availability) {
        Courier courier = new Courier(TestPeople.saved(em, name, "COURIER"), "0771234567");
        courier.setAvailability(availability);
        em.persist(courier);
        return courier;
    }

    private Delivery delivery(Customer customer, String tracking, String city, DeliveryStatus status) {
        Delivery delivery = new Delivery(customer, tracking, UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.setStatus(status);
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Recipient " + city, "0711234567",
                "1 Main Street", city, city, null));
        em.persist(delivery);
        return delivery;
    }
}
