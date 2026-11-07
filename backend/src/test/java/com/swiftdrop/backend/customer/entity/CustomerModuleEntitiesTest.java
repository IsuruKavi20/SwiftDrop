package com.swiftdrop.backend.customer.entity;

import com.swiftdrop.backend.identity.TestPeople;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hibernate creates the tables from the entities at startup;
 * these tests save and read back a whole booking through the entities.
 */
@SpringBootTest
@Transactional
class CustomerModuleEntitiesTest {

    @Autowired
    private EntityManager em;

    @Test
    void savesAndReadsBackACompleteBooking() {
        Customer customer = new Customer(TestPeople.saved(em, "Nimal Perera", "CUSTOMER"), "0771234567");
        CustomerAddress home = new CustomerAddress("Home", "12 Galle Road", "Colombo", "Colombo", "00300");
        home.setDefaultAddress(true);
        customer.addAddress(home);
        em.persist(customer);

        Delivery delivery = new Delivery(customer, "SD-000001", UUID.randomUUID().toString(), DeliveryType.EXPRESS);
        delivery.setSpecialInstructions("Call before arriving");
        delivery.setAddress(DeliveryAddress.from(home, AddressType.PICKUP, "Nimal Perera", "0771234567"));
        delivery.setAddress(new DeliveryAddress(AddressType.DESTINATION, "Kamala Silva", "0719876543",
                "5 Temple Road", "Kandy", "Kandy", "20000"));
        delivery.addParcel(new Parcel("Books", new BigDecimal("2.50"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10")));
        Instant pickedUp = Instant.parse("2026-11-05T08:00:00Z");
        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.PICKED_UP, "Picked up", "Colombo", pickedUp, null));
        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.CONFIRMED, "Booking confirmed", null, pickedUp.minusSeconds(3600), null));
        em.persist(delivery);
        em.flush();
        em.clear();

        Delivery saved = em.find(Delivery.class, delivery.getId());
        assertThat(saved.getCustomer().getUser().getName()).contains("Nimal Perera");
        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPickupAddress()).get().extracting(DeliveryAddress::getCity).isEqualTo("Colombo");
        assertThat(saved.getDestinationAddress()).get().extracting(DeliveryAddress::getContactName).isEqualTo("Kamala Silva");
        assertThat(saved.getParcels()).singleElement().extracting(Parcel::getWeightKg).isEqualTo(new BigDecimal("2.50"));
        // The timeline comes back in time order, not insertion order
        assertThat(saved.getTrackingEvents()).extracting(TrackingEvent::getStatus)
                .containsExactly(DeliveryStatus.CONFIRMED, DeliveryStatus.PICKED_UP);
    }

    @Test
    void editingASavedAddressDoesNotChangePastDeliveries() {
        Customer customer = new Customer(TestPeople.saved(em, "Nimal Perera", "CUSTOMER"), "0771234567");
        CustomerAddress home = new CustomerAddress("Home", "12 Galle Road", "Colombo", "Colombo", null);
        customer.addAddress(home);
        em.persist(customer);
        Delivery delivery = new Delivery(customer, "SD-000002", UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.setAddress(DeliveryAddress.from(home, AddressType.PICKUP, "Nimal Perera", "0771234567"));
        em.persist(delivery);
        em.flush();

        home.setStreet("99 New Street");
        em.flush();
        em.clear();

        assertThat(em.find(Delivery.class, delivery.getId()).getPickupAddress()).get()
                .extracting(DeliveryAddress::getStreet).isEqualTo("12 Galle Road");
    }

    @Test
    void theDatabaseRefusesACourierWhoIsNotAKnownPerson() {
        Customer customer = new Customer(TestPeople.saved(em, "Nimal Perera", "CUSTOMER"), "0771234567");
        em.persist(customer);
        Delivery delivery = new Delivery(customer, "SD-000004", UUID.randomUUID().toString(), DeliveryType.STANDARD);
        delivery.assignCourier(TestPeople.unsaved("Not In app_users", "COURIER"));

        assertThatThrownBy(() -> {
            em.persist(delivery);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void trackingNumbersAreUnique() {
        Customer customer = new Customer(TestPeople.saved(em, "Nimal Perera", "CUSTOMER"), "0771234567");
        em.persist(customer);
        em.persist(new Delivery(customer, "SD-000003", UUID.randomUUID().toString(), DeliveryType.STANDARD));
        Delivery duplicate = new Delivery(customer, "SD-000003", UUID.randomUUID().toString(), DeliveryType.STANDARD);

        assertThatThrownBy(() -> {
            em.persist(duplicate);
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
