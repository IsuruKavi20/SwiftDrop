package com.swiftdrop.backend.dispatcher.service;

import com.swiftdrop.backend.dispatcher.dto.AssignmentResponse;
import com.swiftdrop.backend.dispatcher.dto.CourierCandidateResponse;
import com.swiftdrop.backend.dispatcher.dto.CourierProfileRequest;
import com.swiftdrop.backend.dispatcher.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.entity.CourierAvailability;
import com.swiftdrop.backend.dispatcher.repository.CourierRepository;
import com.swiftdrop.backend.identity.entity.AppUser;
import com.swiftdrop.backend.identity.service.UserDirectory;
import com.swiftdrop.backend.shared.error.ConflictException;
import com.swiftdrop.backend.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates and edits couriers' work profiles (phone, vehicle, availability). Who the courier is (name, account state,
 * roles) stays in the auth service: a profile is only created for an active person with the Courier role, and only
 * such a person can be made Available. Blocking someone or removing the role is done in the auth service.
 */
@Service
@Transactional
public class CourierProfileService {

    private static final String COURIER = "COURIER";

    private final CourierRepository couriers;
    private final UserDirectory userDirectory;
    private final CourierWorkloadService workloads;

    CourierProfileService(CourierRepository couriers, UserDirectory userDirectory, CourierWorkloadService workloads) {
        this.couriers = couriers;
        this.userDirectory = userDirectory;
        this.workloads = workloads;
    }

    /** Active people with the Courier role and no profile yet, by name (from the local copy of the auth service). */
    @Transactional(readOnly = true)
    public List<CourierCandidateResponse> candidates() {
        Set<UUID> profiled = couriers.findAll().stream().map(c -> c.getUser().getId()).collect(Collectors.toSet());
        return userDirectory.listByRole(COURIER).stream()
                .filter(person -> !profiled.contains(person.getId()))
                .map(person -> new CourierCandidateResponse(person.getId(), AssignmentResponse.nameOf(person)))
                .sorted(Comparator.comparing(CourierCandidateResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * 400 without a userId; 404 if the auth service doesn't know the person or they are blocked or deleted;
     * 409 if they lack the Courier role or already have a profile.
     */
    public CourierWorkloadResponse create(CourierProfileRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("Choose a courier account.");
        }
        if (couriers.existsByUserId(request.userId())) {
            throw new ConflictException("This person already has a courier profile.");
        }
        // Copies the person from the auth service if we haven't seen them yet
        AppUser person = userDirectory.require(request.userId());
        if (!person.hasRole(COURIER)) {
            throw new ConflictException(AssignmentResponse.nameOf(person)
                    + " doesn't have the Courier role. Give it in Users and Roles first.");
        }
        Courier courier = new Courier(person, request.phoneNumber().trim());
        apply(courier, request);
        // The unique auth_user_id column stops a second profile created at the same moment (409 from the handler)
        return workloads.one(couriers.saveAndFlush(courier));
    }

    /**
     * Phone, vehicle and availability. The courier row is locked, as assignment does, so an assignment can't slip in
     * while the courier is being taken off duty. Only an active account with the Courier role can be made Available.
     */
    public CourierWorkloadResponse update(Long courierId, CourierProfileRequest request) {
        Courier courier = couriers.findByIdForUpdate(courierId)
                .orElseThrow(() -> new NotFoundException("Courier not found"));
        AppUser person = courier.getUser();
        if (request.availability() == CourierAvailability.AVAILABLE) {
            String name = AssignmentResponse.nameOf(person);
            if (!person.isActive()) {
                throw new ConflictException(name + "'s account is blocked, so they can't be made available.");
            }
            if (!person.hasRole(COURIER)) {
                throw new ConflictException(name + " no longer has the Courier role, so they can't be made available.");
            }
        }
        courier.setPhoneNumber(request.phoneNumber().trim());
        apply(courier, request);
        return workloads.one(couriers.saveAndFlush(courier));
    }

    private static void apply(Courier courier, CourierProfileRequest request) {
        courier.setVehicleType(blankToNull(request.vehicleType()));
        courier.setVehicleNumber(blankToNull(request.vehicleNumber()));
        courier.setAvailability(request.availability());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
