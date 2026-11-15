package com.swiftdrop.backend.dispatcher.controller;

import com.swiftdrop.backend.customer.entity.DeliveryStatus;
import com.swiftdrop.backend.dispatcher.dto.AssignCourierRequest;
import com.swiftdrop.backend.dispatcher.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.dispatcher.dto.DispatchDeliveryDetailsResponse;
import com.swiftdrop.backend.dispatcher.dto.DispatchDeliveryResponse;
import com.swiftdrop.backend.dispatcher.dto.StatusCountsResponse;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.dispatcher.service.CourierAssignmentService;
import com.swiftdrop.backend.dispatcher.service.CourierWorkloadService;
import com.swiftdrop.backend.dispatcher.service.DispatcherDeliveryService;
import com.swiftdrop.backend.shared.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin's dispatch screens: every delivery across customers and merchants, and courier assignment.
 * ADMIN only: in SwiftDrop admins do the dispatch work (the auth service's separate DISPATCHER role is not used,
 * so this checks the role rather than parcel:assign, which DISPATCHER also carries).
 */
@RestController
@RequestMapping("/api/dispatcher")
@PreAuthorize("hasRole('ADMIN')")
class DispatcherController {

    private final DispatcherDeliveryService deliveries;
    private final CourierAssignmentService assignments;
    private final CourierWorkloadService workloads;

    DispatcherController(DispatcherDeliveryService deliveries, CourierAssignmentService assignments,
                         CourierWorkloadService workloads) {
        this.deliveries = deliveries;
        this.assignments = assignments;
        this.workloads = workloads;
    }

    /** e.g. GET /api/dispatcher/deliveries?status=FAILED&search=kandy&page=0&size=10 (newest first, size max 50) */
    @GetMapping("/deliveries")
    PageResponse<DispatchDeliveryResponse> search(@RequestParam(required = false) DeliveryStatus status,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return deliveries.search(status, search, page, size);
    }

    /** Confirmed deliveries with no courier, oldest first. */
    @GetMapping("/deliveries/unassigned")
    PageResponse<DispatchDeliveryResponse> unassigned(@RequestParam(required = false) String search,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return deliveries.unassigned(search, page, size);
    }

    @GetMapping("/deliveries/counts")
    StatusCountsResponse counts() {
        return deliveries.counts();
    }

    @GetMapping("/deliveries/{deliveryId}")
    DispatchDeliveryDetailsResponse details(@PathVariable Long deliveryId) {
        return deliveries.details(deliveryId);
    }

    /** Assign or reassign; answers with the delivery and its updated assignment history. */
    @PostMapping("/deliveries/{deliveryId}/assignment")
    DispatchDeliveryDetailsResponse assign(@PathVariable Long deliveryId, @Valid @RequestBody AssignCourierRequest request,
                                           @AuthenticationPrincipal Jwt user) {
        return assignments.assign(deliveryId, request, user);
    }

    /** Courier profiles with their workload; optionally only one availability. */
    @GetMapping("/couriers")
    List<CourierWorkloadResponse> couriers(@RequestParam(required = false) CourierAvailability availability) {
        return workloads.workloads(availability);
    }

    /** Couriers who can take a delivery now, lightest workload first. */
    @GetMapping("/couriers/assignable")
    List<CourierWorkloadResponse> assignable() {
        return workloads.assignable();
    }
}
