package com.swiftdrop.backend.dispatcher.service;

import com.swiftdrop.backend.customer.entity.Delivery;
import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.dispatcher.dto.AssignmentResponse;
import com.swiftdrop.backend.dispatcher.dto.DispatchDeliveryDetailsResponse;
import com.swiftdrop.backend.dispatcher.dto.DispatchDeliveryResponse;
import com.swiftdrop.backend.dispatcher.dto.DispatchDeliveryResponse.AssignedCourier;
import com.swiftdrop.backend.dispatcher.dto.StatusCountsResponse;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.dispatcher.repository.CourierRepository;
import com.swiftdrop.backend.dispatcher.repository.DispatcherDeliveryRepository;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.shared.dto.PageResponse;
import com.swiftdrop.backend.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the admin's dispatch screens: every delivery across customers and merchants. Callers are checked in
 * the controller (ADMIN only); this service does not filter by owner.
 */
@Service
@Transactional(readOnly = true)
public class DispatcherDeliveryService {

    static final int MAX_PAGE_SIZE = 50;

    private final DispatcherDeliveryRepository deliveries;
    private final CourierRepository couriers;
    private final CourierAssignmentRepository assignments;

    DispatcherDeliveryService(DispatcherDeliveryRepository deliveries, CourierRepository couriers,
                              CourierAssignmentRepository assignments) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.assignments = assignments;
    }

    /** All deliveries, newest first; status and text are optional (text: tracking number, courier, contacts, places). */
    public PageResponse<DispatchDeliveryResponse> search(DeliveryStatus status, String text, int page, int size) {
        return toPage(deliveries.search(status, text, pageRequest(page, size)));
    }

    /** Confirmed deliveries with no courier yet, oldest first. */
    public PageResponse<DispatchDeliveryResponse> unassigned(String text, int page, int size) {
        return toPage(deliveries.searchUnassigned(text, pageRequest(page, size)));
    }

    public StatusCountsResponse counts() {
        Map<DeliveryStatus, Long> statuses = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            statuses.put(status, 0L);
        }
        deliveries.countByStatus().forEach(count -> statuses.put(count.getStatus(), count.getTotal()));
        return new StatusCountsResponse(statuses, deliveries.countByStatusAndCourierIsNull(DeliveryStatus.CONFIRMED));
    }

    public DispatchDeliveryDetailsResponse details(Long deliveryId) {
        Delivery delivery = deliveries.findById(deliveryId).orElseThrow(() -> new NotFoundException("Delivery not found"));
        return detailsOf(delivery);
    }

    /** The delivery with its assignment history, newest first. Call inside a transaction. */
    DispatchDeliveryDetailsResponse detailsOf(Delivery delivery) {
        List<AssignmentResponse> history = assignments
                .findByDeliveryIdOrderByAssignedAtDescIdDesc(delivery.getId(), Pageable.unpaged())
                .map(AssignmentResponse::from)
                .getContent();
        return new DispatchDeliveryDetailsResponse(
                DispatchDeliveryResponse.from(delivery, courierOf(delivery, profilesFor(List.of(delivery)))), history);
    }

    private PageResponse<DispatchDeliveryResponse> toPage(Page<Delivery> page) {
        Map<UUID, Courier> profiles = profilesFor(page.getContent());
        return PageResponse.from(page, delivery -> DispatchDeliveryResponse.from(delivery, courierOf(delivery, profiles)));
    }

    /** Courier profiles of the people on these deliveries, in one query. */
    private Map<UUID, Courier> profilesFor(List<Delivery> page) {
        List<UUID> people = page.stream().map(Delivery::getCourier).filter(Objects::nonNull).map(AppUser::getId)
                .distinct().toList();
        if (people.isEmpty()) {
            return Map.of();
        }
        return couriers.findByUserIdIn(people).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), Function.identity()));
    }

    private static AssignedCourier courierOf(Delivery delivery, Map<UUID, Courier> profiles) {
        AppUser person = delivery.getCourier();
        if (person == null) {
            return null;
        }
        Courier profile = profiles.get(person.getId());
        return new AssignedCourier(profile == null ? null : profile.getId(), AssignmentResponse.nameOf(person));
    }

    static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
