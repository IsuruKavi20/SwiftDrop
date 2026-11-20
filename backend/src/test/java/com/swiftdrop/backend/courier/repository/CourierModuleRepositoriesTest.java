package com.swiftdrop.backend.courier.repository;

import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import com.swiftdrop.backend.courier.entity.DeliveryProof;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CourierModuleRepositoriesTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierProfileRepository courierProfiles;

    @Autowired
    private DeliveryProofRepository deliveryProofs;

    @Autowired
    private DeliveryFailureRepository deliveryFailures;

    @Autowired
    private CourierDeliveryRepository courierDeliveries;

    private AppUser courierUser;
    private AppUser customerUser;
    private Customer customer;
    private Delivery assignedDelivery;

    @BeforeEach
    void setUp() {
        courierUser = TestPeople.saved(em, "Sachin Maleesha", "COURIER");
        customerUser = TestPeople.saved(em, "Alice Perera", "CUSTOMER");

        customer = new Customer(customerUser, "Alice", "Perera", "alice@example.com", "+94771234567");
        em.persist(customer);

        assignedDelivery = new Delivery(customer, "SD-1001-TEST", "token-test-1001", DeliveryType.STANDARD);
        assignedDelivery.assignCourier(courierUser);
        assignedDelivery.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        em.persist(assignedDelivery);

        em.flush();
    }

    @Test
    void courierProfileRepository_savesAndFindsByUserIdAndZone() {
        CourierProfile profile = new CourierProfile(courierUser, "+94779876543", "MOTORCYCLE", "Colombo Central");
        courierProfiles.save(profile);
        em.flush();

        Optional<CourierProfile> found = courierProfiles.findByUserId(courierUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getVehicleType()).isEqualTo("MOTORCYCLE");
        assertThat(found.get().getAssignedZone()).isEqualTo("Colombo Central");
        assertThat(found.get().isActive()).isTrue();

        List<CourierProfile> inZone = courierProfiles.findByAssignedZoneIgnoreCaseAndActiveTrue("colombo central");
        assertThat(inZone).hasSize(1);
        assertThat(inZone.get(0).getUserId()).isEqualTo(courierUser.getId());

        assertThat(courierProfiles.existsByUserId(courierUser.getId())).isTrue();
    }

    @Test
    void deliveryProofRepository_savesAndFindsByDeliveryId() {
        DeliveryProof proof = new DeliveryProof(assignedDelivery, courierUser, "SIGNATURE", "Kamal Silva",
                "Delivered to recipient in person", "https://media.swiftdrop.me/proofs/test.png");
        deliveryProofs.save(proof);
        em.flush();

        Optional<DeliveryProof> found = deliveryProofs.findByDeliveryId(assignedDelivery.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRecipientSignName()).isEqualTo("Kamal Silva");
        assertThat(found.get().getProofType()).isEqualTo("SIGNATURE");
        assertThat(found.get().getCourier().getId()).isEqualTo(courierUser.getId());
        assertThat(deliveryProofs.existsByDeliveryId(assignedDelivery.getId())).isTrue();
    }

    @Test
    void deliveryFailureRepository_savesAndFindsByDeliveryId() {
        DeliveryFailure failure = new DeliveryFailure(assignedDelivery, courierUser,
                DeliveryFailureReason.RECIPIENT_UNAVAILABLE, "Called 3 times, gate locked");
        deliveryFailures.save(failure);
        em.flush();

        List<DeliveryFailure> history = deliveryFailures.findByDeliveryIdOrderByReportedAtDesc(assignedDelivery.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFailureReason()).isEqualTo(DeliveryFailureReason.RECIPIENT_UNAVAILABLE);
        assertThat(history.get(0).getFailureRemarks()).isEqualTo("Called 3 times, gate locked");
        assertThat(history.get(0).getCourier().getId()).isEqualTo(courierUser.getId());
    }

    @Test
    void courierDeliveryRepository_queriesDeliveriesByCourierAndStatus() {
        var page = courierDeliveries.findByCourierIdOrderByCreatedAtDesc(courierUser.getId(), PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTrackingNumber()).isEqualTo("SD-1001-TEST");

        Optional<Delivery> found = courierDeliveries.findByTrackingNumberAndCourierId("SD-1001-TEST", courierUser.getId());
        assertThat(found).isPresent();

        long count = courierDeliveries.countByCourierIdAndStatus(courierUser.getId(), DeliveryStatus.OUT_FOR_DELIVERY);
        assertThat(count).isEqualTo(1);

        Delivery unassigned = new Delivery(customer, "SD-1002-TEST", "token-test-1002", DeliveryType.STANDARD);
        unassigned.setStatus(DeliveryStatus.CONFIRMED);
        em.persist(unassigned);
        em.flush();

        List<Delivery> availableForDispatcher = courierDeliveries.findByCourierIsNullAndStatus(DeliveryStatus.CONFIRMED);
        assertThat(availableForDispatcher).extracting(Delivery::getTrackingNumber).contains("SD-1002-TEST");
    }
}
