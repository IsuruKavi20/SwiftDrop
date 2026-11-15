package com.swiftdrop.backend.dispatcher.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.dispatcher.dto.AssignCourierRequest;
import com.swiftdrop.backend.dispatcher.dto.AssignmentResponse;
import com.swiftdrop.backend.dispatcher.dto.DispatchDeliveryDetailsResponse;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.dispatcher.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.dispatcher.repository.CourierRepository;
import com.swiftdrop.backend.dispatcher.repository.DispatcherDeliveryRepository;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.shared.error.ConflictException;
import com.swiftdrop.backend.shared.error.NotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Assigns a courier to a delivery, or reassigns it. A reassignment ends the current CourierAssignment (who, when,
 * why) and creates a new active one; Delivery.courier follows it in the same transaction.
 *
 * <p>The delivery's status is not changed: the courier records the next step (picked up, out for delivery…).
 * A FAILED delivery therefore stays FAILED, with its new courier, until that courier's next attempt.
 *
 * <p>Locking (docs/DISPATCHER-REPOSITORIES.md): the delivery first, then the couriers involved in id order, so two
 * admins assigning at the same moment are served one after the other and never both succeed.
 */
@Service
public class CourierAssignmentService {

    /** Confirmed, under way, or failed (for another attempt). Pending, delivered and cancelled ones can't be assigned. */
    static final Set<DeliveryStatus> ASSIGNABLE = EnumSet.of(DeliveryStatus.CONFIRMED, DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT, DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.FAILED);

    private final DispatcherDeliveryRepository deliveries;
    private final CourierRepository couriers;
    private final CourierAssignmentRepository assignments;
    private final UserDirectory userDirectory;
    private final DispatcherDeliveryService reads;

    CourierAssignmentService(DispatcherDeliveryRepository deliveries, CourierRepository couriers,
                             CourierAssignmentRepository assignments, UserDirectory userDirectory,
                             DispatcherDeliveryService reads) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.assignments = assignments;
        this.userDirectory = userDirectory;
        this.reads = reads;
    }

    @Transactional
    public DispatchDeliveryDetailsResponse assign(Long deliveryId, AssignCourierRequest request, Jwt caller) {
        AppUser admin = userDirectory.caller(caller);
        Delivery delivery = deliveries.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
        if (!ASSIGNABLE.contains(delivery.getStatus())) {
            throw new ConflictException("Only confirmed, in-progress or failed deliveries can be assigned. This one is "
                    + delivery.getStatus() + ".");
        }

        Optional<CourierAssignment> current = assignments.findActiveByDeliveryIdForUpdate(deliveryId);
        Map<Long, Courier> locked = lockCouriers(request.courierId(), current.map(a -> a.getCourier().getId()).orElse(null));
        Courier courier = Optional.ofNullable(locked.get(request.courierId()))
                .orElseThrow(() -> new NotFoundException("Courier not found"));
        String name = AssignmentResponse.nameOf(courier.getUser());

        if (current.isPresent() && current.get().getCourier().getId().equals(courier.getId())) {
            throw new ConflictException(name + " is already assigned to this delivery.");
        }
        if (courier.getAvailability() != CourierAvailability.AVAILABLE) {
            throw new ConflictException(name + " is not available right now.");
        }
        // Checks the account is still usable (not blocked or deleted): 404 if not
        AppUser person = userDirectory.require(courier.getUser().getId());
        if (!person.hasRole("COURIER")) {
            throw new ConflictException(name + " no longer has the Courier role.");
        }

        String reason = request.reason() == null ? "" : request.reason().trim();
        boolean reassigning = current.isPresent() || delivery.getCourier() != null;
        if (reassigning && reason.isEmpty()) {
            throw new IllegalArgumentException("Give a reason for the reassignment.");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("Keep the reason under 500 characters.");
        }

        Instant now = Instant.now();
        // End and flush the old row first: only one active row per delivery is allowed
        current.ifPresent(old -> {
            old.endAssignment(admin, now, reason);
            assignments.saveAndFlush(old);
        });
        assignments.save(new CourierAssignment(delivery, courier, admin, now));
        delivery.assignCourier(person);
        deliveries.flush();
        return reads.detailsOf(delivery);
    }

    /** Locks the new courier and the current one (if any) in id order; missing ids are simply absent. */
    private Map<Long, Courier> lockCouriers(Long newCourierId, Long currentCourierId) {
        Set<Long> ids = new TreeSet<>();
        ids.add(newCourierId);
        if (currentCourierId != null) {
            ids.add(currentCourierId);
        }
        Map<Long, Courier> locked = new HashMap<>();
        for (Long id : ids) {
            couriers.findByIdForUpdate(id).ifPresent(courier -> locked.put(id, courier));
        }
        return locked;
    }
}
