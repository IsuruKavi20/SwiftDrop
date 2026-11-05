package com.swiftdrop.backend.customer.repository;

import com.swiftdrop.backend.customer.entity.AddressType;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.CustomerAddress;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryAddress;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CustomerModuleRepositoriesTest {

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerAddressRepository addresses;

    @Autowired
    private CustomerDeliveryRepository deliveries;

    @Autowired
    private TrackingEventRepository trackingEvents;

    private Customer alice;
    private Customer bob;
    private Delivery aliceToKandy;
    private Delivery aliceToGalle;
    private Delivery bobsDelivery;

    @BeforeEach
    void twoCustomersWithDeliveries() {
        alice = new Customer(UUID.randomUUID(), "Alice", "Fernando", "alice@example.com", "0771111111");
        alice.addAddress(new CustomerAddress("Office", "1 Main St", "Colombo", "Colombo", null));
        CustomerAddress home = new CustomerAddress("Home", "2 Lake Rd", "Colombo", "Colombo", null);
        home.setDefaultAddress(true);
        alice.addAddress(home);
        customers.save(alice);
        bob = customers.save(new Customer(UUID.randomUUID(), "Bob", "Silva", "bob@example.com", "0772222222"));

        aliceToKandy = delivery(alice, "SD-100001", "Kamal Perera", "Kandy");
        aliceToKandy.addTrackingEvent(new TrackingEvent(DeliveryStatus.CONFIRMED, "Booking confirmed", null,
                Instant.parse("2026-11-01T08:00:00Z"), null));
        aliceToKandy.addTrackingEvent(new TrackingEvent(DeliveryStatus.IN_TRANSIT, "Left Colombo hub", "Colombo",
                Instant.parse("2026-11-02T08:00:00Z"), null));
        deliveries.save(aliceToKandy);
        aliceToGalle = deliveries.save(delivery(alice, "SD-100002", "Nisha Jayasuriya", "Galle"));
        bobsDelivery = deliveries.save(delivery(bob, "SD-100003", "Ruwan Kumara", "Kandy"));
    }

    private static Delivery delivery(Customer owner, String trackingNumber, String receiver, String city) {
        Delivery delivery = new Delivery(owner, trackingNumber, UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, receiver, "0710000000", "1 Some Rd", city, city, null));
        return delivery;
    }

    @Test
    void findsTheLoggedInCustomerByTheirTokenSubject() {
        assertThat(customers.findByAuthUserId(alice.getAuthUserId())).get().extracting(Customer::getEmail).isEqualTo("alice@example.com");
        assertThat(customers.existsByEmailIgnoreCase("ALICE@example.com")).isTrue();
    }

    @Test
    void savedAddressesListTheDefaultFirstAndStayPrivate() {
        assertThat(addresses.findByCustomerIdOrderByDefaultAddressDescLabelAsc(alice.getId()))
                .extracting(CustomerAddress::getLabel).containsExactly("Home", "Office");
        Long alicesAddress = alice.getAddresses().getFirst().getId();
        assertThat(addresses.findByIdAndCustomerId(alicesAddress, bob.getId())).isEmpty();
        assertThat(addresses.findByCustomerIdAndDefaultAddressTrue(alice.getId())).get()
                .extracting(CustomerAddress::getLabel).isEqualTo("Home");
    }

    @Test
    void myDeliveriesListsOnlyTheCustomersOwn() {
        assertThat(deliveries.findByCustomerIdOrderByCreatedAtDesc(alice.getId(), Pageable.unpaged()))
                .extracting(Delivery::getTrackingNumber).containsExactlyInAnyOrder("SD-100001", "SD-100002");
    }

    @Test
    void aCustomerCannotLoadAnotherCustomersDelivery() {
        assertThat(deliveries.findByIdAndCustomerId(bobsDelivery.getId(), alice.getId())).isEmpty();
        assertThat(deliveries.findByTrackingNumberAndCustomerId("SD-100003", alice.getId())).isEmpty();
        assertThat(deliveries.findByIdAndCustomerId(aliceToKandy.getId(), alice.getId())).isPresent();
    }

    @Test
    void searchMatchesTrackingNumberReceiverOrCityAndFiltersByStatus() {
        assertThat(deliveries.search(alice.getId(), null, "100002", Pageable.unpaged()))
                .extracting(Delivery::getTrackingNumber).containsExactly("SD-100002");
        assertThat(deliveries.search(alice.getId(), null, "kamal", Pageable.unpaged()))
                .extracting(Delivery::getTrackingNumber).containsExactly("SD-100001");
        // "Kandy" matches Bob's delivery too, but search only covers Alice's
        assertThat(deliveries.search(alice.getId(), null, "KANDY", Pageable.unpaged()))
                .extracting(Delivery::getTrackingNumber).containsExactly("SD-100001");
        assertThat(deliveries.search(alice.getId(), DeliveryStatus.PENDING, null, Pageable.unpaged()))
                .extracting(Delivery::getTrackingNumber).containsExactly("SD-100002");
        assertThat(deliveries.search(alice.getId(), null, null, Pageable.unpaged())).hasSize(2);
        assertThat(deliveries.search(alice.getId(), null, "  ", Pageable.unpaged())).hasSize(2);
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsPlainCharacters() {
        assertThat(deliveries.search(alice.getId(), null, "%", Pageable.unpaged())).isEmpty();
        assertThat(deliveries.search(alice.getId(), null, "SD_1", Pageable.unpaged())).isEmpty();
        assertThat(CustomerDeliveryRepository.containsPattern(" Kan_dy% ")).isEqualTo("%kan\\_dy\\%%");
    }

    @Test
    void dashboardCountsDeliveriesByStatus() {
        Map<DeliveryStatus, Long> counts = deliveries.countByStatus(alice.getId()).stream()
                .collect(Collectors.toMap(CustomerDeliveryRepository.StatusCount::getStatus,
                        CustomerDeliveryRepository.StatusCount::getTotal));
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(DeliveryStatus.IN_TRANSIT, 1L, DeliveryStatus.PENDING, 1L));
    }

    @Test
    void publicTrackingFindsByTokenOrTrackingNumber() {
        assertThat(deliveries.findByPublicTrackingToken(aliceToGalle.getPublicTrackingToken())).get()
                .extracting(Delivery::getTrackingNumber).isEqualTo("SD-100002");
        assertThat(deliveries.findByPublicTrackingToken("not-a-real-token")).isEmpty();
        assertThat(deliveries.findByTrackingNumber("SD-100003")).isPresent();
        assertThat(deliveries.existsByTrackingNumber("SD-100001")).isTrue();
    }

    @Test
    void trackingTimelineIsInTimeOrder() {
        assertThat(trackingEvents.findByDeliveryIdOrderByEventTimeAsc(aliceToKandy.getId()))
                .extracting(TrackingEvent::getStatus).containsExactly(DeliveryStatus.CONFIRMED, DeliveryStatus.IN_TRANSIT);
        assertThat(trackingEvents.findFirstByDeliveryIdOrderByEventTimeDesc(aliceToKandy.getId())).get()
                .extracting(TrackingEvent::getLocationName).isEqualTo("Colombo");
    }
}
