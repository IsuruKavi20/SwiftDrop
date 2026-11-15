package com.swiftdrop.backend.dispatcher.controller;

import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Reports page's API: Sri Lanka days, delivered by delivery date, failed attempts from tracking events. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReportApiTest {

    /** "Now" is 15 Nov 2026, 08:30 in Colombo (03:00 UTC). */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        AppUser admin = TestPeople.saved(em, "Amaya Admin", "ADMIN");
        Courier kasun = courier("Kasun Fernando");
        Courier ravi = courier("Ravi Silva");
        courier("Chamara Bandara");
        Customer alice = new Customer(TestPeople.saved(em, "Alice Customer", "CUSTOMER"), "0771234567");
        em.persist(alice);

        // Booked 15 Nov 01:30 in Colombo (still 14 Nov in UTC), delivered by Kasun at 02:30
        Delivery today = delivery(alice, "SD-400001");
        today.assignCourier(kasun.getUser());
        today.addTrackingEvent(new TrackingEvent(DeliveryStatus.DELIVERED, "Delivered", null,
                Instant.parse("2026-11-14T21:00:00Z"), kasun.getUser()));

        // Booked 13 Nov; Ravi's attempt failed that day, then an admin recorded another failure on 14 Nov.
        // Kasun carries it now: Ravi's failure stays Ravi's, the admin-recorded one goes to the current courier.
        Delivery failing = delivery(alice, "SD-400002");
        failing.addTrackingEvent(new TrackingEvent(DeliveryStatus.FAILED, "Nobody home", null,
                Instant.parse("2026-11-13T06:00:00Z"), ravi.getUser()));
        failing.addTrackingEvent(new TrackingEvent(DeliveryStatus.FAILED, "Address closed", null,
                Instant.parse("2026-11-14T06:00:00Z"), admin));
        failing.assignCourier(kasun.getUser());
        em.persist(new CourierAssignment(failing, kasun, admin, Instant.parse("2026-11-14T07:00:00Z")));

        // Booked and delivered by Ravi at the start of the month: only in the 30-day report
        Delivery old = delivery(alice, "SD-400003");
        old.assignCourier(ravi.getUser());
        old.addTrackingEvent(new TrackingEvent(DeliveryStatus.DELIVERED, "Delivered", null,
                Instant.parse("2026-11-02T05:00:00Z"), ravi.getUser()));
        em.flush();

        created("SD-400001", "2026-11-14 20:00:00");
        created("SD-400002", "2026-11-13 05:00:00");
        created("SD-400003", "2026-11-01 05:00:00");
        em.clear();
    }

    @Test
    void onlyAdminsSeeReports() throws Exception {
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reports").with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sevenDayReportCountsBySriLankaDay() throws Exception {
        mockMvc.perform(get("/api/admin/reports?days=7").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-09"))
                .andExpect(jsonPath("$.to").value("2026-11-15"))
                .andExpect(jsonPath("$.bookingsCreated").value(2))
                .andExpect(jsonPath("$.delivered").value(1))
                .andExpect(jsonPath("$.failedAttempts").value(2))
                .andExpect(jsonPath("$.perDay", hasSize(7)))
                .andExpect(jsonPath("$.perDay[0].day").value("2026-11-09"))
                .andExpect(jsonPath("$.perDay[4].day").value("2026-11-13"))
                .andExpect(jsonPath("$.perDay[4].bookings").value(1))
                .andExpect(jsonPath("$.perDay[4].failedAttempts").value(1))
                .andExpect(jsonPath("$.perDay[5].failedAttempts").value(1))
                .andExpect(jsonPath("$.perDay[5].bookings").value(0))
                .andExpect(jsonPath("$.perDay[6].day").value("2026-11-15"))
                .andExpect(jsonPath("$.perDay[6].bookings").value(1))
                .andExpect(jsonPath("$.perDay[6].delivered").value(1))
                // Most deliveries first, then by name; every courier listed
                .andExpect(jsonPath("$.couriers[*].name", contains("Kasun Fernando", "Chamara Bandara", "Ravi Silva")))
                .andExpect(jsonPath("$.couriers[0].delivered").value(1))
                .andExpect(jsonPath("$.couriers[0].failedAttempts").value(1))
                .andExpect(jsonPath("$.couriers[0].active").value(1))
                .andExpect(jsonPath("$.couriers[2].failedAttempts").value(1))
                .andExpect(jsonPath("$.couriers[2].active").value(0));
    }

    @Test
    void oneDayIsTodayAndThirtyDaysReachFurther() throws Exception {
        mockMvc.perform(get("/api/admin/reports?days=1").with(admin()))
                .andExpect(jsonPath("$.from").value("2026-11-15"))
                .andExpect(jsonPath("$.bookingsCreated").value(1))
                .andExpect(jsonPath("$.delivered").value(1))
                .andExpect(jsonPath("$.failedAttempts").value(0))
                .andExpect(jsonPath("$.perDay", hasSize(1)));
        mockMvc.perform(get("/api/admin/reports?days=30").with(admin()))
                .andExpect(jsonPath("$.from").value("2026-10-17"))
                .andExpect(jsonPath("$.bookingsCreated").value(3))
                .andExpect(jsonPath("$.delivered").value(2))
                .andExpect(jsonPath("$.couriers[*].name", contains("Kasun Fernando", "Ravi Silva", "Chamara Bandara")));
    }

    @Test
    void refusesPeriodsOutsideOneToNinetyDays() throws Exception {
        mockMvc.perform(get("/api/admin/reports?days=0").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose between 1 and 90 days."));
        mockMvc.perform(get("/api/admin/reports?days=91").with(admin())).andExpect(status().isBadRequest());
    }

    private Courier courier(String name) {
        Courier courier = new Courier(TestPeople.saved(em, name, "COURIER"), "0771234567");
        courier.setAvailability(CourierAvailability.AVAILABLE);
        em.persist(courier);
        return courier;
    }

    private Delivery delivery(Customer customer, String tracking) {
        Delivery delivery = new Delivery(customer, tracking, UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.setStatus(DeliveryStatus.CONFIRMED);
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Recipient", "0711234567", "1 Main Street",
                "Kandy", "Kandy", null));
        em.persist(delivery);
        return delivery;
    }

    /** created_at is set by the database on insert; move it to the test's time (UTC). */
    private void created(String tracking, String utc) {
        em.createNativeQuery("update delivery set created_at = TIMESTAMP WITH TIME ZONE '" + utc + "+00:00' where tracking_number = :tn")
                .setParameter("tn", tracking).executeUpdate();
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
