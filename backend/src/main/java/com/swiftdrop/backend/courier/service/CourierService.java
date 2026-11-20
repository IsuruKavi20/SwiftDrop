package com.swiftdrop.backend.courier.service;

import com.swiftdrop.backend.courier.dto.CourierDashboardResponse;
import com.swiftdrop.backend.courier.dto.CourierDeliveryDetailResponse;
import com.swiftdrop.backend.courier.dto.CourierDeliverySummaryResponse;
import com.swiftdrop.backend.courier.dto.CourierProfileProvisionRequest;
import com.swiftdrop.backend.courier.dto.CourierProfileResponse;
import com.swiftdrop.backend.courier.dto.CourierStatusUpdateRequest;
import com.swiftdrop.backend.courier.dto.DeliveryConfirmationRequest;
import com.swiftdrop.backend.courier.dto.DeliveryFailureRequest;
import com.swiftdrop.backend.courier.entity.CourierProfile;
import com.swiftdrop.backend.courier.entity.DeliveryFailure;
import com.swiftdrop.backend.courier.entity.DeliveryProof;
import com.swiftdrop.backend.courier.exception.InvalidStatusTransitionException;
import com.swiftdrop.backend.courier.exception.UnauthorizedDeliveryAccessException;
import com.swiftdrop.backend.courier.repository.CourierDeliveryRepository;
import com.swiftdrop.backend.courier.repository.CourierProfileRepository;
import com.swiftdrop.backend.courier.repository.DeliveryFailureRepository;
import com.swiftdrop.backend.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.customer.entity.TrackingEvent;
import com.swiftdrop.backend.shared.error.NotFoundException;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Business logic layer for Courier operations:
 * Profile, dashboard metrics, assigned deliveries retrieval, lifecycle state transitions,
 * delivery confirmation with proof, and failure audit reporting.
 */
@Service
@Transactional
public class CourierService {

    private final CourierDeliveryRepository courierDeliveries;
    private final CourierProfileRepository courierProfiles;
    private final DeliveryProofRepository deliveryProofs;
    private final DeliveryFailureRepository deliveryFailures;
    private final UserDirectory userDirectory;

    public CourierService(CourierDeliveryRepository courierDeliveries,
                          CourierProfileRepository courierProfiles,
                          DeliveryProofRepository deliveryProofs,
                          DeliveryFailureRepository deliveryFailures,
                          UserDirectory userDirectory) {
        this.courierDeliveries = courierDeliveries;
        this.courierProfiles = courierProfiles;
        this.deliveryProofs = deliveryProofs;
        this.deliveryFailures = deliveryFailures;
        this.userDirectory = userDirectory;
    }

    /**
     * Retrieves the authenticated courier's operational profile.
     */
    @Transactional(readOnly = true)
    public CourierProfileResponse getProfile(UUID courierId) {
        AppUser user = userDirectory.require(courierId);
        CourierProfile profile = courierProfiles.findByUserId(courierId).orElse(null);
        String email = null;
        try {
            email = userDirectory.email(courierId).orElse(null);
        } catch (Exception ignored) {
            // Live directory service may be unconfigured in local or test environments
        }
        return CourierProfileResponse.from(profile, user, email);
    }

    /**
     * Derives whether the currently authenticated caller possesses Dispatcher or Administrator authority.
     * Evaluated securely against the active Spring SecurityContextHolder.
     */
    private boolean isDispatcherOrAdminCaller() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_DISPATCHER".equalsIgnoreCase(a)
                        || "ROLE_ADMIN".equalsIgnoreCase(a)
                        || "delivery:assign".equalsIgnoreCase(a));
    }

    /**
     * Self-service profile update for couriers.
     * Enforces that couriers may update only approved personal profile fields (phone number, vehicle type).
     * Attempts to self-assign operational delivery zones or toggle active dispatch eligibility are strictly rejected
     * unless the authenticated caller possesses Dispatcher or Administrator authority.
     */
    public CourierProfileResponse updateOwnProfile(UUID courierId, CourierProfileProvisionRequest request) {
        boolean isDispatcherOrAdmin = isDispatcherOrAdminCaller();
        return doProvisionOrUpdateProfile(courierId, request, isDispatcherOrAdmin);
    }

    /**
     * Dispatcher and Administrator operational profile provisioning.
     * Dedicated service boundary for assigning operational zones, vehicle assignments, and active eligibility.
     * Requires DISPATCHER or ADMIN role, or delivery:assign authority.
     */
    public CourierProfileResponse provisionOperationalProfile(UUID courierId, CourierProfileProvisionRequest request) {
        if (!isDispatcherOrAdminCaller()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Operational courier provisioning requires Dispatcher or Administrator authority.");
        }
        return doProvisionOrUpdateProfile(courierId, request, true);
    }

    /**
     * General profile provisioning method for internal service coordination.
     * Derives authorization from the verified security context rather than caller-supplied flags.
     */
    public CourierProfileResponse provisionProfile(UUID courierId, CourierProfileProvisionRequest request) {
        boolean isDispatcherOrAdmin = isDispatcherOrAdminCaller();
        return doProvisionOrUpdateProfile(courierId, request, isDispatcherOrAdmin);
    }

    private CourierProfileResponse doProvisionOrUpdateProfile(UUID courierId, CourierProfileProvisionRequest request, boolean isDispatcherOrAdmin) {
        AppUser user = userDirectory.require(courierId);
        if (!user.isActive()) {
            throw new IllegalArgumentException("Cannot provision courier profile for inactive user account");
        }
        if (!user.getRoles().contains("COURIER")) {
            throw new IllegalArgumentException("Only users with the COURIER role may have a courier profile");
        }

        CourierProfile profile = courierProfiles.findByUserId(courierId).orElse(null);

        // Security check: Couriers cannot self-assign operational zone or active status
        if (!isDispatcherOrAdmin) {
            boolean attemptingZoneChange = request.assignedZone() != null
                    && !request.assignedZone().isBlank()
                    && (profile == null || !request.assignedZone().trim().equalsIgnoreCase(profile.getAssignedZone()));
            boolean attemptingActiveChange = request.active() != null
                    && (profile == null || request.active() != profile.isActive());

            if (attemptingZoneChange || attemptingActiveChange) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Couriers cannot self-assign operational delivery zones or dispatch eligibility; this action requires Dispatcher or Administrator authority.");
            }
        }

        if (profile == null) {
            String zone = (isDispatcherOrAdmin && request.assignedZone() != null && !request.assignedZone().isBlank())
                    ? request.assignedZone().trim()
                    : "Unassigned";
            boolean active = (request.active() != null) ? request.active() : isDispatcherOrAdmin;
            profile = new CourierProfile(user, request.phoneNumber().trim(), request.vehicleType().trim(), zone);
            profile.setActive(active);
        } else {
            profile.setPhoneNumber(request.phoneNumber().trim());
            profile.setVehicleType(request.vehicleType().trim());
            if (isDispatcherOrAdmin) {
                if (request.assignedZone() != null && !request.assignedZone().isBlank()) {
                    profile.setAssignedZone(request.assignedZone().trim());
                }
                if (request.active() != null) {
                    profile.setActive(request.active());
                }
            }
        }
        courierProfiles.save(profile);

        String email = null;
        try {
            email = userDirectory.email(courierId).orElse(null);
        } catch (Exception ignored) {
        }
        return CourierProfileResponse.from(profile, user, email);
    }

    /**
     * Lists active, eligible couriers optionally filtered by delivery zone.
     * Excludes inactive users and couriers with "Unassigned" operational zones.
     * Enables Dispatcher assignment workflows and operational routing.
     */
    @Transactional(readOnly = true)
    public List<CourierProfileResponse> getEligibleCouriers(String zone) {
        List<CourierProfile> profiles = (zone != null && !zone.isBlank())
                ? courierProfiles.findEligibleCouriersByZone(zone.trim())
                : courierProfiles.findEligibleCouriers();

        return profiles.stream()
                .map(p -> {
                    String email = null;
                    try {
                        email = userDirectory.email(p.getUserId()).orElse(null);
                    } catch (Exception ignored) {
                    }
                    return CourierProfileResponse.from(p, p.getUser(), email);
                })
                .toList();
    }

    /**
     * Computes today's workload and completion statistics for the courier dashboard.
     */
    @Transactional(readOnly = true)
    public CourierDashboardResponse getDashboardStats(UUID courierId) {
        userDirectory.require(courierId);

        long pickedUp = courierDeliveries.countByCourierIdAndStatus(courierId, DeliveryStatus.PICKED_UP);
        long inTransit = courierDeliveries.countByCourierIdAndStatus(courierId, DeliveryStatus.IN_TRANSIT);
        long outForDelivery = courierDeliveries.countByCourierIdAndStatus(courierId, DeliveryStatus.OUT_FOR_DELIVERY);
        long inProgress = pickedUp + inTransit + outForDelivery;

        long delivered = courierDeliveries.countByCourierIdAndStatus(courierId, DeliveryStatus.DELIVERED);
        long failed = courierDeliveries.countByCourierIdAndStatus(courierId, DeliveryStatus.FAILED);

        // All deliveries assigned to courier created or active today
        List<Delivery> allAssigned = courierDeliveries.findByCourierIdOrderByCreatedAtDesc(courierId, Pageable.unpaged()).getContent();
        LocalDate today = LocalDate.now();
        long assignedToday = allAssigned.stream()
                .filter(d -> d.getCreatedAt() != null &&
                        d.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(today))
                .count();

        // If no deliveries created today, fall back to total assigned workload for completion context
        long totalReference = assignedToday > 0 ? assignedToday : (inProgress + delivered + failed);
        int completionPercentage = totalReference > 0
                ? (int) Math.round(((double) delivered / totalReference) * 100)
                : 0;

        return new CourierDashboardResponse(
                assignedToday > 0 ? assignedToday : totalReference,
                inProgress,
                delivered,
                failed,
                completionPercentage
        );
    }

    /**
     * Retrieves assigned deliveries for the courier with optional status filter and text search.
     */
    @Transactional(readOnly = true)
    public PageResponse<CourierDeliverySummaryResponse> getAssignedDeliveries(
            UUID courierId,
            DeliveryStatus status,
            String search,
            Pageable pageable) {
        userDirectory.require(courierId);
        String pattern = (search == null || search.isBlank()) ? "%" : "%" + search.trim().toLowerCase() + "%";
        Page<Delivery> page = courierDeliveries.searchByCourier(courierId, status, pattern, pageable);
        return PageResponse.from(page, CourierDeliverySummaryResponse::from);
    }

    /**
     * Retrieves full delivery details, parcel dimensions, status history, and any proof/failure logs.
     * Enforces courier ownership.
     */
    @Transactional(readOnly = true)
    public CourierDeliveryDetailResponse getDeliveryDetails(UUID courierId, String trackingNumber) {
        userDirectory.require(courierId);
        Delivery delivery = requireCourierDelivery(trackingNumber, courierId);
        DeliveryProof proof = deliveryProofs.findByDeliveryId(delivery.getId()).orElse(null);
        List<DeliveryFailure> failures = deliveryFailures.findByDeliveryIdOrderByReportedAtDesc(delivery.getId());
        return CourierDeliveryDetailResponse.from(delivery, proof, failures);
    }

    /**
     * Advances delivery status along permitted lifecycle stages (PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY).
     * Terminal statuses (DELIVERED, FAILED) must be submitted via their dedicated endpoints with audit evidence.
     */
    public CourierDeliveryDetailResponse updateDeliveryStatus(
            UUID courierId,
            String trackingNumber,
            CourierStatusUpdateRequest request) {
        AppUser courier = userDirectory.require(courierId);
        Delivery delivery = requireCourierDelivery(trackingNumber, courierId);

        DeliveryStatus next = request.nextStatus();
        if (next == DeliveryStatus.DELIVERED) {
            throw new InvalidStatusTransitionException(trackingNumber, delivery.getStatus(), next);
        }
        if (next == DeliveryStatus.FAILED) {
            throw new InvalidStatusTransitionException(trackingNumber, delivery.getStatus(), next);
        }

        if (!isTransitionAllowed(delivery.getStatus(), next)) {
            throw new InvalidStatusTransitionException(trackingNumber, delivery.getStatus(), next);
        }

        String location = (request.location() != null && !request.location().isBlank())
                ? request.location().trim()
                : "In Transit";
        String description = (request.notes() != null && !request.notes().isBlank())
                ? request.notes().trim()
                : "Status updated to " + next;

        delivery.addTrackingEvent(new TrackingEvent(next, description, location, Instant.now(), courier));
        courierDeliveries.save(delivery);

        DeliveryProof proof = deliveryProofs.findByDeliveryId(delivery.getId()).orElse(null);
        List<DeliveryFailure> failures = deliveryFailures.findByDeliveryIdOrderByReportedAtDesc(delivery.getId());
        return CourierDeliveryDetailResponse.from(delivery, proof, failures);
    }

    /**
     * Confirms successful parcel delivery with recipient signature and optional delivery notes.
     */
    public CourierDeliveryDetailResponse confirmDelivery(
            UUID courierId,
            String trackingNumber,
            DeliveryConfirmationRequest request) {
        AppUser courier = userDirectory.require(courierId);
        Delivery delivery = requireCourierDelivery(trackingNumber, courierId);

        if (!isTransitionAllowed(delivery.getStatus(), DeliveryStatus.DELIVERED)) {
            throw new InvalidStatusTransitionException(trackingNumber, delivery.getStatus(), DeliveryStatus.DELIVERED);
        }

        String proofType = (request.proofType() != null && !request.proofType().isBlank())
                ? request.proofType().trim()
                : "SIGNATURE";

        DeliveryProof proof = new DeliveryProof(
                delivery,
                courier,
                proofType,
                request.recipientSignName().trim(),
                request.deliveryNotes(),
                request.proofMediaUrl()
        );
        deliveryProofs.save(proof);

        String location = (request.location() != null && !request.location().isBlank())
                ? request.location().trim()
                : "Destination Address";
        String description = "Delivered to " + request.recipientSignName().trim() +
                (request.deliveryNotes() != null && !request.deliveryNotes().isBlank()
                        ? " (" + request.deliveryNotes().trim() + ")"
                        : "");

        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.DELIVERED, description, location, Instant.now(), courier));
        courierDeliveries.save(delivery);

        List<DeliveryFailure> failures = deliveryFailures.findByDeliveryIdOrderByReportedAtDesc(delivery.getId());
        return CourierDeliveryDetailResponse.from(delivery, proof, failures);
    }

    /**
     * Records an unsuccessful delivery attempt with validated failure reason and remarks.
     */
    public CourierDeliveryDetailResponse reportDeliveryFailure(
            UUID courierId,
            String trackingNumber,
            DeliveryFailureRequest request) {
        AppUser courier = userDirectory.require(courierId);
        Delivery delivery = requireCourierDelivery(trackingNumber, courierId);

        if (!isTransitionAllowed(delivery.getStatus(), DeliveryStatus.FAILED)) {
            throw new InvalidStatusTransitionException(trackingNumber, delivery.getStatus(), DeliveryStatus.FAILED);
        }

        DeliveryFailure failure = new DeliveryFailure(
                delivery,
                courier,
                request.failureReason(),
                request.failureRemarks().trim()
        );
        deliveryFailures.save(failure);

        String location = (request.location() != null && !request.location().isBlank())
                ? request.location().trim()
                : "Delivery Attempt Location";
        String description = "Delivery failed: " + request.failureReason() + " - " + request.failureRemarks().trim();

        delivery.addTrackingEvent(new TrackingEvent(DeliveryStatus.FAILED, description, location, Instant.now(), courier));
        courierDeliveries.save(delivery);

        DeliveryProof proof = deliveryProofs.findByDeliveryId(delivery.getId()).orElse(null);
        List<DeliveryFailure> failures = deliveryFailures.findByDeliveryIdOrderByReportedAtDesc(delivery.getId());
        return CourierDeliveryDetailResponse.from(delivery, proof, failures);
    }

    /**
     * Permitted state transition rules for Courier lifecycle:
     * - PENDING / CONFIRMED -> PICKED_UP
     * - PICKED_UP           -> IN_TRANSIT
     * - IN_TRANSIT          -> OUT_FOR_DELIVERY, FAILED
     * - OUT_FOR_DELIVERY    -> DELIVERED (via confirmDelivery), FAILED (via reportDeliveryFailure), IN_TRANSIT
     * - FAILED              -> OUT_FOR_DELIVERY (reattempt during shift), IN_TRANSIT (return to hub)
     * - DELIVERED, CANCELLED -> Terminal (no transitions permitted)
     */
    public boolean isTransitionAllowed(DeliveryStatus current, DeliveryStatus next) {
        if (current == null || next == null || current == next) {
            return false;
        }
        return switch (current) {
            case PENDING, CONFIRMED -> next == DeliveryStatus.PICKED_UP;
            case PICKED_UP -> next == DeliveryStatus.IN_TRANSIT;
            case IN_TRANSIT -> next == DeliveryStatus.OUT_FOR_DELIVERY || next == DeliveryStatus.FAILED;
            case OUT_FOR_DELIVERY -> next == DeliveryStatus.DELIVERED || next == DeliveryStatus.FAILED || next == DeliveryStatus.IN_TRANSIT;
            case FAILED -> next == DeliveryStatus.OUT_FOR_DELIVERY || next == DeliveryStatus.IN_TRANSIT;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /**
     * Validates that the delivery exists and is assigned to the authenticated courier.
     * Throws 404 if not found, or 403 if assigned to another user or unassigned.
     */
    private Delivery requireCourierDelivery(String trackingNumber, UUID courierId) {
        Delivery delivery = courierDeliveries.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NotFoundException("Delivery not found with tracking number: " + trackingNumber));

        if (delivery.getCourier() == null || !delivery.getCourier().getId().equals(courierId)) {
            throw new UnauthorizedDeliveryAccessException(trackingNumber, courierId);
        }
        return delivery;
    }
}

