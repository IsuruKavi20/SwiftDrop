package com.swiftdrop.backend.dispatcher.service;

import com.swiftdrop.backend.dispatcher.dto.AssignmentResponse;
import com.swiftdrop.backend.dispatcher.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAssignment;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.dispatcher.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.dispatcher.repository.CourierRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Couriers with their current workload, for Courier Workloads and the assign dialog. Read-only: creating and editing
 * courier profiles is a separate piece of work.
 */
@Service
@Transactional(readOnly = true)
public class CourierWorkloadService {

    private final CourierRepository couriers;
    private final CourierAssignmentRepository assignments;

    CourierWorkloadService(CourierRepository couriers, CourierAssignmentRepository assignments) {
        this.couriers = couriers;
        this.assignments = assignments;
    }

    /** Every courier profile (or only one availability), by id. Workload counts active assignments only. */
    public List<CourierWorkloadResponse> workloads(CourierAvailability availability) {
        List<Courier> list = availability == null
                ? couriers.findAll(Sort.by("id"))
                : couriers.findByAvailabilityOrderByIdAsc(availability, Pageable.unpaged()).getContent();
        return withWorkload(list);
    }

    /**
     * Who can take a delivery now: Available couriers whose account is active and still has the Courier role,
     * lightest workload first. The assignment itself checks all of this again under a lock.
     */
    public List<CourierWorkloadResponse> assignable() {
        return withWorkload(couriers.findAvailableForAssignment(Pageable.unpaged()).getContent()).stream()
                .sorted(Comparator.comparingInt(CourierWorkloadResponse::activeDeliveries)
                        .thenComparing(CourierWorkloadResponse::courierId))
                .toList();
    }

    private List<CourierWorkloadResponse> withWorkload(List<Courier> list) {
        Map<Long, List<String>> tracking = assignments.findAllActiveWithDelivery().stream()
                .collect(Collectors.groupingBy(a -> a.getCourier().getId(),
                        Collectors.mapping((CourierAssignment a) -> a.getDelivery().getTrackingNumber(), Collectors.toList())));
        return list.stream().map(c -> {
            List<String> current = tracking.getOrDefault(c.getId(), List.of());
            return new CourierWorkloadResponse(c.getId(), AssignmentResponse.nameOf(c.getUser()), c.getPhoneNumber(),
                    c.getVehicleType(), c.getVehicleNumber(), c.getAvailability(), current.size(), current);
        }).toList();
    }
}
