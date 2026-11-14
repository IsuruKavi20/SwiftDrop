package com.swiftdrop.backend.customer.service;

import jakarta.persistence.EntityManager;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import com.swiftdrop.backend.customer.dto.CreateDeliveryRequest;
import com.swiftdrop.backend.customer.dto.CustomerAddressRequest;
import com.swiftdrop.backend.customer.dto.CustomerAddressResponse;
import com.swiftdrop.backend.customer.dto.DashboardResponse;
import com.swiftdrop.backend.customer.dto.DeliveryAddressRequest;
import com.swiftdrop.backend.customer.dto.DeliveryDetailsResponse;
import com.swiftdrop.backend.customer.dto.DeliverySummaryResponse;
import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.customer.dto.ParcelRequest;
import com.swiftdrop.backend.customer.dto.RegisterCustomerRequest;
import com.swiftdrop.backend.customer.dto.TrackingEventResponse;
import com.swiftdrop.backend.customer.dto.TrackingResponse;
import com.swiftdrop.backend.customer.dto.UpdateCustomerProfileRequest;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.exception.ConflictException;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerModuleServicesTest {

    /** Today is 5 November 2026, 10:00 UTC, for every test. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private EntityManager em;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private DeliveryBookingService bookingService;

    @Autowired
    private CustomerDeliveryService deliveryService;

    @Autowired
    private PublicTrackingService publicTracking;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void registerTwoCustomers() {
        customerService.register(TestPeople.saved(em, alice, "Alice Fernando", "CUSTOMER"),
                new RegisterCustomerRequest(" 0771111111 ", null, new CustomerAddressRequest("Home", "2 Lake Rd", "Colombo", "Colombo", null, null)));
        customerService.register(TestPeople.saved(em, bob, "Bob Silva", "CUSTOMER"),
                new RegisterCustomerRequest("0772222222", null, new CustomerAddressRequest("Home", "2 Lake Rd", "Colombo", "Colombo", null, null)));
    }

    private static CreateDeliveryRequest booking(DeliveryType type, String receiver, String city) {
        return new CreateDeliveryRequest(type,
                new DeliveryAddressRequest("Alice Fernando", "0771111111", "1 Main St", "Colombo", "Colombo", ""),
                new DeliveryAddressRequest(receiver, "0719999999", "5 Temple Rd", city, city, null),
                List.of(new ParcelRequest("Books", new BigDecimal("2.50"), new BigDecimal("30"), new BigDecimal("20"),
                        new BigDecimal("10"), null)),
                "  ");
    }

    // ---- Registration and profile ----

    @Test
    void registrationTakesTheNameFromTheLoginAccountAndRejectsDuplicates() {
        assertThat(customerService.getProfile(alice).name()).isEqualTo("Alice Fernando");
        assertThat(customerService.getProfile(alice).phoneNumber()).isEqualTo("0771111111");

        AppUser aliceAgain = em.find(AppUser.class, alice);
        assertThatThrownBy(() -> customerService.register(aliceAgain, new RegisterCustomerRequest("0771111111", null, new CustomerAddressRequest("Home", "2 Lake Rd", "Colombo", "Colombo", null, null))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already registered");
    }

    @Test
    void anUnregisteredUserGetsNotFoundEverywhere() {
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> customerService.getProfile(stranger)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> bookingService.createDelivery(stranger, booking(DeliveryType.STANDARD, "X", "Kandy")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deliveryService.getDashboard(stranger)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesTheProfile() {
        customerService.updateProfile(alice, new UpdateCustomerProfileRequest("0773333333"));
        assertThat(customerService.getProfile(alice).phoneNumber()).isEqualTo("0773333333");
    }

    // ---- Saved addresses ----

    @Test
    void theRegistrationAddressIsDefaultAndANewDefaultReplacesIt() {
        CustomerAddressResponse office = customerService.addAddress(alice,
                new CustomerAddressRequest("Office", "1 Main St", "Colombo", "Colombo", null, false));
        assertThat(office.defaultAddress()).isFalse();   // "Home" from registration is already the default

        customerService.addAddress(alice, new CustomerAddressRequest("Work", "9 Bay Rd", "Colombo", "Colombo", null, true));

        assertThat(customerService.listAddresses(alice))
                .extracting(CustomerAddressResponse::label, CustomerAddressResponse::defaultAddress)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Work", true),
                        org.assertj.core.groups.Tuple.tuple("Home", false), org.assertj.core.groups.Tuple.tuple("Office", false));
    }

    @Test
    void aCustomerCannotTouchAnotherCustomersAddress() {
        Long alicesHome = customerService.addAddress(alice,
                new CustomerAddressRequest("Office", "1 Main St", "Colombo", "Colombo", null, false)).id();
        CustomerAddressRequest change = new CustomerAddressRequest("Mine", "x", "x", "x", null, false);

        assertThatThrownBy(() -> customerService.updateAddress(bob, alicesHome, change)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> customerService.deleteAddress(bob, alicesHome)).isInstanceOf(NotFoundException.class);

        customerService.deleteAddress(alice, alicesHome);
        assertThat(customerService.listAddresses(alice)).extracting(CustomerAddressResponse::label).containsExactly("Home");
    }

    // ---- Booking and confirmation ----

    @Test
    void createsAPendingBookingWithGeneratedCodes() {
        DeliveryDetailsResponse booked = bookingService.createDelivery(alice, booking(DeliveryType.EXPRESS, "Kamal Perera", "Kandy"));

        assertThat(booked.id()).isNotNull();
        assertThat(booked.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(booked.trackingNumber()).matches("SD-[A-Z2-9]{10}");
        assertThat(booked.publicTrackingToken()).hasSize(43);
        assertThat(booked.estimatedDeliveryDate()).isEqualTo(LocalDate.of(2026, 11, 6)); // express: next day
        assertThat(booked.specialInstructions()).isNull(); // blank becomes null
        assertThat(booked.pickup().postalCode()).isNull();
        assertThat(booked.destination().contactName()).isEqualTo("Kamal Perera");
        assertThat(booked.parcels()).hasSize(1);
        assertThat(booked.trackingEvents()).extracting(TrackingEventResponse::status).containsExactly(DeliveryStatus.PENDING);
    }

    @Test
    void confirmingMovesTheBookingToConfirmedOnlyOnce() {
        Long id = bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Kamal", "Kandy")).id();

        DeliveryDetailsResponse confirmed = bookingService.confirmDelivery(alice, id);
        assertThat(confirmed.status()).isEqualTo(DeliveryStatus.CONFIRMED);
        assertThat(confirmed.trackingEvents()).extracting(TrackingEventResponse::status)
                .containsExactly(DeliveryStatus.PENDING, DeliveryStatus.CONFIRMED);

        assertThatThrownBy(() -> bookingService.confirmDelivery(alice, id))
                .isInstanceOf(ConflictException.class).hasMessageContaining("CONFIRMED");
    }

    @Test
    void aCustomerCannotSeeOrConfirmAnotherCustomersDelivery() {
        DeliveryDetailsResponse alicesDelivery = bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Kamal", "Kandy"));

        assertThatThrownBy(() -> bookingService.confirmDelivery(bob, alicesDelivery.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deliveryService.getDeliveryDetails(bob, alicesDelivery.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deliveryService.trackMyDelivery(bob, alicesDelivery.trackingNumber())).isInstanceOf(NotFoundException.class);
        assertThat(deliveryService.getMyDeliveries(bob, null, null, 0, 10).totalElements()).isZero();
    }

    // ---- Dashboard, My Deliveries, tracking ----

    @Test
    void dashboardSummarisesTheCustomersDeliveries() {
        Long first = bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Kamal", "Kandy")).id();
        bookingService.createDelivery(alice, booking(DeliveryType.SAME_DAY, "Nisha", "Galle"));
        bookingService.confirmDelivery(alice, first);
        bookingService.createDelivery(bob, booking(DeliveryType.STANDARD, "Ruwan", "Jaffna"));

        DashboardResponse dashboard = deliveryService.getDashboard(alice);

        assertThat(dashboard.totalDeliveries()).isEqualTo(2);
        assertThat(dashboard.activeDeliveries()).isEqualTo(1);
        assertThat(dashboard.countsByStatus()).containsEntry(DeliveryStatus.PENDING, 1L).containsEntry(DeliveryStatus.CONFIRMED, 1L);
        assertThat(dashboard.recentDeliveries()).extracting(DeliverySummaryResponse::receiverName)
                .containsExactlyInAnyOrder("Kamal", "Nisha");
    }

    @Test
    void myDeliveriesSearchesFiltersAndPages() {
        for (int i = 1; i <= 3; i++) {
            bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Receiver " + i, "Kandy"));
        }
        bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Nisha", "Galle"));

        PageResponse<DeliverySummaryResponse> firstPage = deliveryService.getMyDeliveries(alice, null, null, 0, 3);
        assertThat(firstPage.content()).hasSize(3);
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        assertThat(deliveryService.getMyDeliveries(alice, null, "galle", 0, 10).content())
                .extracting(DeliverySummaryResponse::receiverName).containsExactly("Nisha");
        assertThat(deliveryService.getMyDeliveries(alice, DeliveryStatus.CONFIRMED, " ", 0, 10).content()).isEmpty();
        // An over-large page size is capped
        assertThat(deliveryService.getMyDeliveries(alice, null, null, 0, 10_000).size()).isEqualTo(CustomerDeliveryService.MAX_PAGE_SIZE);
    }

    @Test
    void customerTrackingAcceptsAnyCaseAndSpaces() {
        String trackingNumber = bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Kamal", "Kandy")).trackingNumber();

        TrackingResponse tracking = deliveryService.trackMyDelivery(alice, "  " + trackingNumber.toLowerCase() + " ");

        assertThat(tracking.trackingNumber()).isEqualTo(trackingNumber);
        assertThat(tracking.toCity()).isEqualTo("Kandy");
    }

    @Test
    void publicTrackingWorksByTokenOrNumberWithoutLogin() {
        DeliveryDetailsResponse booked = bookingService.createDelivery(alice, booking(DeliveryType.STANDARD, "Kamal", "Kandy"));

        assertThat(publicTracking.trackByPublicToken(booked.publicTrackingToken()).status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(publicTracking.trackByTrackingNumber(booked.trackingNumber()).fromCity()).isEqualTo("Colombo");
        assertThatThrownBy(() -> publicTracking.trackByPublicToken("guessed-token")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> publicTracking.trackByPublicToken(" ")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> publicTracking.trackByTrackingNumber("SD-NOTREAL00")).isInstanceOf(NotFoundException.class);
    }
}
