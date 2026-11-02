package com.swiftdrop.backend.delivery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Who sees which delivery is decided by the token's permissions (entitlements), never by trusting the client:
 * "delivery:read-all" (dispatcher, admin) sees every delivery; "delivery:read-own" (courier) only those assigned
 * to the caller's own user id. Assigning needs "parcel:assign".
 */
@RestController
@RequestMapping("/api/deliveries")
class DeliveryController {

    private final DeliveryRepository deliveries;

    DeliveryController(DeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('delivery:read-all', 'delivery:read-own')")
    Delivery get(@PathVariable UUID id, JwtAuthenticationToken caller) {
        Delivery delivery = deliveries.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!canReadAll(caller) && !delivery.courierId().equals(userId(caller.getToken()))) {
            // The brief's rule: a courier never sees another courier's delivery
            throw new AccessDeniedException("Not your delivery");
        }
        return delivery;
    }

    // Dispatchers and admins: every delivery
    @GetMapping
    @PreAuthorize("hasAuthority('delivery:read-all')")
    List<Delivery> all() {
        return deliveries.findAll();
    }

    // Couriers: only the deliveries assigned to them
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('delivery:read-own')")
    List<Delivery> mine(@AuthenticationPrincipal Jwt caller) {
        return deliveries.findByCourierId(userId(caller));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('parcel:assign')")
    Delivery assign(@Valid @RequestBody AssignDeliveryRequest request) {
        return deliveries.save(new Delivery(UUID.randomUUID(), request.parcelReference(), request.courierId(), "ASSIGNED"));
    }

    record AssignDeliveryRequest(@NotBlank @Size(max = 64) String parcelReference, @NotNull UUID courierId) {
    }

    private static boolean canReadAll(JwtAuthenticationToken caller) {
        return caller.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("delivery:read-all"::equals);
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
