package com.swiftdrop.backend.courier.service;

import com.swiftdrop.backend.courier.dto.CourierDeliveryDetailResponse;
import com.swiftdrop.backend.courier.dto.CourierStatusUpdateRequest;
import com.swiftdrop.backend.courier.dto.DeliveryConfirmationRequest;
import com.swiftdrop.backend.courier.dto.DeliveryFailureRequest;
import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.courier.entity.DeliveryFailureReason;
import com.swiftdrop.backend.courier.entity.DeliveryProof;
import com.swiftdrop.backend.courier.exception.InvalidStatusTransitionException;
import com.swiftdrop.backend.courier.exception.UnauthorizedDeliveryAccessException;
import com.swiftdrop.backend.courier.repository.CourierDeliveryRepository;
import com.swiftdrop.backend.courier.repository.CourierProfileRepository;
import com.swiftdrop.backend.courier.repository.DeliveryFailureRepository;
import com.swiftdrop.backend.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.customer.entity.Customer;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.DeliveryType;
import com.swiftdrop.backend.customer.exception.NotFoundException;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.entity.TestPeople;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CourierServiceTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CourierService courierService;

    @Autowired
    private CourierDeliveryRepository courierDeliveries;

    @Autowired
    private CourierProfileRepository courierProfiles;

    @Autowired
    private DeliveryProofRepository deliveryProofs;

    @Autowired
    private DeliveryFailureRepository deliveryFailures;

    private AppUser courier1;
    private AppUser courier2;
    private Customer customer;
    private Delivery delivery1;
    private Delivery delivery2;

    @BeforeEach
    void setUp() {
        courier1 = TestPeople.saved(em, "Sachin Maleesha", "COURIER");
        courier2 = TestPeople.saved(em, "Kamal Perera", "COURIER");
        AppUser customerUser = TestPeople.saved(em, "Nimal Silva", "CUSTOMER");

        customer = new Customer(customerUser, "Nimal", "Silva", "nimal@example.com", "+94771112233");
        em.persist(customer);

        CourierProfile profile1 = new CourierProfile(courier1, "+94771234567", "MOTORCYCLE", "Colombo Central");
        courierProfiles.save(profile1);

        // Delivery 1 assigned to Courier 1
        delivery1 = new Delivery(customer, "SD-1001", "token-1001", DeliveryType.STANDARD);
        delivery1.assignCourier(courier1);
        delivery1.setStatus(DeliveryStatus.CONFIRMED);
        em.persist(delivery1);

        // Delivery 2 assigned to Courier 2
        delivery2 = new Delivery(customer, "SD-2002", "token-2002", DeliveryType.EXPRESS);
        delivery2.assignCourier(courier2);
        delivery2.setStatus(DeliveryStatus.CONFIRMED);
        em.persist(delivery2);

        em.flush();
    }

    @Test
    @DisplayName("1. Retrieve the current courier's assigned deliveries")
    void getAssignedDeliveries_returnsOnlyCallerDeliveries() {
        var page = courierService.getAssignedDeliveries(courier1.getId(), null, null, PageRequest.of(0, 10));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).trackingNumber()).isEqualTo("SD-1001");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("2. Prevent reading another courier's delivery")
    void getDeliveryDetails_rejectsAccessToAnotherCouriersDelivery() {
        assertThatThrownBy(() -> courierService.getDeliveryDetails(courier1.getId(), "SD-2002"))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("3. Prevent updating another courier's delivery")
    void updateDeliveryStatus_rejectsUpdatingAnotherCouriersDelivery() {
        CourierStatusUpdateRequest request = new CourierStatusUpdateRequest(
                DeliveryStatus.PICKED_UP, "Colombo Hub", "Picked up by courier");

        assertThatThrownBy(() -> courierService.updateDeliveryStatus(courier1.getId(), "SD-2002", request))
                .isInstanceOf(UnauthorizedDeliveryAccessException.class)
                .hasMessageContaining("not authorized");

        // Verify status remains unchanged
        Delivery unchanged = courierDeliveries.findByTrackingNumber("SD-2002").orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
    }

    @Test
    @DisplayName("4. Accept permitted status transitions along the lifecycle graph")
    void updateDeliveryStatus_acceptsPermittedTransitions() {
        // Step 1: CONFIRMED -> PICKED_UP
        CourierDeliveryDetailResponse res1 = courierService.updateDeliveryStatus(
                courier1.getId(), "SD-1001",
                new CourierStatusUpdateRequest(DeliveryStatus.PICKED_UP, "Merchant Store", "Collected parcel"));
        assertThat(res1.status()).isEqualTo(DeliveryStatus.PICKED_UP);

        // Step 2: PICKED_UP -> IN_TRANSIT
        CourierDeliveryDetailResponse res2 = courierService.updateDeliveryStatus(
                courier1.getId(), "SD-1001",
                new CourierStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, "Colombo Highway", "On way to destination"));
        assertThat(res2.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);

        // Step 3: IN_TRANSIT -> OUT_FOR_DELIVERY
        CourierDeliveryDetailResponse res3 = courierService.updateDeliveryStatus(
                courier1.getId(), "SD-1001",
                new CourierStatusUpdateRequest(DeliveryStatus.OUT_FOR_DELIVERY, "Colombo 03", "Arrived in neighborhood"));
        assertThat(res3.status()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @DisplayName("5. Reject invalid status transitions")
    void updateDeliveryStatus_rejectsInvalidTransitions() {
        // Direct jump from CONFIRMED -> OUT_FOR_DELIVERY is invalid
        CourierStatusUpdateRequest invalidJump = new CourierStatusUpdateRequest(
                DeliveryStatus.OUT_FOR_DELIVERY, "Colombo", "Invalid skip");

        assertThatThrownBy(() -> courierService.updateDeliveryStatus(courier1.getId(), "SD-1001", invalidJump))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot transition delivery 'SD-1001' from CONFIRMED to OUT_FOR_DELIVERY");

        // Cannot update directly to DELIVERED via general status update endpoint
        CourierStatusUpdateRequest bypassDeliver = new CourierStatusUpdateRequest(
                DeliveryStatus.DELIVERED, "Destination", "Bypass proof");
        assertThatThrownBy(() -> courierService.updateDeliveryStatus(courier1.getId(), "SD-1001", bypassDeliver))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("6. Persist successful delivery confirmation with proof")
    void confirmDelivery_persistsProofAndMarksDelivered() {
        delivery1.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        em.flush();

        DeliveryConfirmationRequest request = new DeliveryConfirmationRequest(
                "Sunil Fernando", "SIGNATURE", "Received in good condition",
                "https://media.swiftdrop.me/proofs/sign-1001.png", "Colombo 03");

        CourierDeliveryDetailResponse response = courierService.confirmDelivery(courier1.getId(), "SD-1001", request);

        assertThat(response.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(response.deliveredAt()).isNotNull();
        assertThat(response.proof()).isNotNull();
        assertThat(response.proof().recipientSignName()).isEqualTo("Sunil Fernando");
        assertThat(response.proof().proofType()).isEqualTo("SIGNATURE");

        // Verify DB persistence
        DeliveryProof proof = deliveryProofs.findByDeliveryId(delivery1.getId()).orElseThrow();
        assertThat(proof.getRecipientSignName()).isEqualTo("Sunil Fernando");
        assertThat(proof.getCourier().getId()).isEqualTo(courier1.getId());
    }

    @Test
    @DisplayName("7. Reject delivery confirmation or failure on terminal statuses")
    void confirmDelivery_rejectsWhenNotOutForDelivery() {
        // Currently in CONFIRMED: cannot confirm delivery directly
        DeliveryConfirmationRequest request = new DeliveryConfirmationRequest(
                "Sunil Fernando", "SIGNATURE", null, null, null);

        assertThatThrownBy(() -> courierService.confirmDelivery(courier1.getId(), "SD-1001", request))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("8. Persist valid delivery failure information")
    void reportDeliveryFailure_persistsFailureAndMarksFailed() {
        delivery1.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        em.flush();

        DeliveryFailureRequest request = new DeliveryFailureRequest(
                DeliveryFailureReason.RECIPIENT_UNAVAILABLE,
                "Customer phone unreachable after 3 attempts at 14:00, 14:15, 14:30",
                "Recipient Gate");

        CourierDeliveryDetailResponse response = courierService.reportDeliveryFailure(courier1.getId(), "SD-1001", request);

        assertThat(response.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.latestFailure()).isNotNull();
        assertThat(response.latestFailure().failureReason()).isEqualTo(DeliveryFailureReason.RECIPIENT_UNAVAILABLE);
        assertThat(response.latestFailure().failureRemarks()).contains("unreachable");

        // Verify DB persistence
        var failures = deliveryFailures.findByDeliveryIdOrderByReportedAtDesc(delivery1.getId());
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getCourier().getId()).isEqualTo(courier1.getId());
    }

    @Test
    @DisplayName("9. Verify consistency when a mutation fails")
    void consistency_whenMutationFails_statusRemainsUnchanged() {
        delivery1.setStatus(DeliveryStatus.PICKED_UP);
        em.flush();

        // Attempt invalid jump from PICKED_UP to DELIVERED
        DeliveryConfirmationRequest invalidConfirm = new DeliveryConfirmationRequest(
                "Nobody", "SIGNATURE", null, null, null);

        assertThatThrownBy(() -> courierService.confirmDelivery(courier1.getId(), "SD-1001", invalidConfirm))
                .isInstanceOf(InvalidStatusTransitionException.class);

        // Verify delivery status was NOT corrupted
        Delivery check = courierDeliveries.findByTrackingNumber("SD-1001").orElseThrow();
        assertThat(check.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(deliveryProofs.findByDeliveryId(delivery1.getId())).isEmpty();
    }

    @Test
    @DisplayName("10. Verify that tracking history accurately represents accepted status changes")
    void trackingHistory_accuratelyRepresentsFullLifecycle() {
        // Step 1: Pickup
        courierService.updateDeliveryStatus(courier1.getId(), "SD-1001",
                new CourierStatusUpdateRequest(DeliveryStatus.PICKED_UP, "Merchant Hub", "Picked up from merchant"));

        // Step 2: In transit
        courierService.updateDeliveryStatus(courier1.getId(), "SD-1001",
                new CourierStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, "Central Sorting", "In transit to regional hub"));

        // Step 3: Out for delivery
        courierService.updateDeliveryStatus(courier1.getId(), "SD-1001",
                new CourierStatusUpdateRequest(DeliveryStatus.OUT_FOR_DELIVERY, "Colombo 07", "Out for final delivery"));

        // Step 4: Delivered
        CourierDeliveryDetailResponse detail = courierService.confirmDelivery(courier1.getId(), "SD-1001",
                new DeliveryConfirmationRequest("Perera", "SIGNATURE", "Signed at door", null, "Colombo 07"));

        // Assert full audit trail
        assertThat(detail.statusHistory()).hasSize(4);
        assertThat(detail.statusHistory()).extracting("status")
                .containsExactly(
                        DeliveryStatus.DELIVERED,
                        DeliveryStatus.OUT_FOR_DELIVERY,
                        DeliveryStatus.IN_TRANSIT,
                        DeliveryStatus.PICKED_UP
                );
        assertThat(detail.statusHistory()).extracting("recordedByName")
                .allMatch(name -> name.equals("Sachin Maleesha"));
    }

    @Test
    @DisplayName("11. Couriers updating own profile cannot change operational zone without Dispatcher authority")
    void updateOwnProfile_rejectsZoneChangeWithoutDispatcherAuthority() {
        var unauthorizedZoneChange = new com.swiftdrop.backend.courier.dto.CourierProfileProvisionRequest(
                "+94770009988", "MOTORCYCLE", "Galle Fort", false);

        assertThatThrownBy(() -> courierService.updateOwnProfile(courier1.getId(), unauthorizedZoneChange))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Couriers cannot self-assign operational delivery zones");

        // Updating phone number within current zone succeeds
        var validPhoneUpdate = new com.swiftdrop.backend.courier.dto.CourierProfileProvisionRequest(
                "+94770009988", "MOTORCYCLE", "Colombo Central", null);
        var response = courierService.updateOwnProfile(courier1.getId(), validPhoneUpdate);
        assertThat(response.phoneNumber()).isEqualTo("+94770009988");
    }

    @Test
    @DisplayName("12. Operational provisioning requires Dispatcher or Administrator authority")
    void provisionOperationalProfile_rejectsUnprivilegedCaller() {
        var request = new com.swiftdrop.backend.courier.dto.CourierProfileProvisionRequest(
                "+94779998877", "VAN", "Kandy", true);

        // Without Dispatcher/Admin in SecurityContextHolder, provisionOperationalProfile is denied
        assertThatThrownBy(() -> courierService.provisionOperationalProfile(courier1.getId(), request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Operational courier provisioning requires Dispatcher or Administrator authority");
    }

    @Test
    @DisplayName("13. Exclude inactive users and couriers with unassigned zone from eligible pool")
    void getEligibleCouriers_excludesInactiveUsersAndUnassignedZone() {
        // Create courier with "Unassigned" zone
        AppUser unassignedCourier = TestPeople.saved(em, "Unassigned Guy", "COURIER");
        CourierProfile unassignedProfile = new CourierProfile(unassignedCourier, "+94771239999", "MOTORCYCLE", "Unassigned");
        unassignedProfile.setActive(true);
        courierProfiles.save(unassignedProfile);

        // Create inactive user with courier profile
        AppUser inactiveCourier = TestPeople.saved(em, "Inactive Guy", "COURIER");
        em.createNativeQuery("UPDATE app_users SET active = false WHERE id = :id")
                .setParameter("id", inactiveCourier.getId())
                .executeUpdate();
        em.clear();
        AppUser reloaded = em.find(AppUser.class, inactiveCourier.getId());
        CourierProfile inactiveProfile = new CourierProfile(reloaded, "+94771238888", "MOTORCYCLE", "Colombo Central");
        inactiveProfile.setActive(true);
        courierProfiles.save(inactiveProfile);

        em.flush();

        var eligible = courierService.getEligibleCouriers(null);
        assertThat(eligible).extracting("fullName")
                .contains("Sachin Maleesha")
                .doesNotContain("Unassigned Guy", "Inactive Guy");
    }
}

