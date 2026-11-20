package com.swiftdrop.backend.dispatcher.controller;

import com.swiftdrop.backend.dispatcher.dto.DeliveryZoneRequest;
import com.swiftdrop.backend.dispatcher.dto.DeliveryZoneResponse;
import com.swiftdrop.backend.dispatcher.service.DeliveryZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The admin's Delivery Zones page. ADMIN only. */
@RestController
@RequestMapping("/api/admin/zones")
@PreAuthorize("hasRole('ADMIN')")
class AdminZoneController {

    private final DeliveryZoneService zones;

    AdminZoneController(DeliveryZoneService zones) {
        this.zones = zones;
    }

    @GetMapping
    List<DeliveryZoneResponse> list() {
        return zones.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DeliveryZoneResponse create(@Valid @RequestBody DeliveryZoneRequest request) {
        return zones.create(request);
    }

    @PutMapping("/{zoneId}")
    DeliveryZoneResponse update(@PathVariable Long zoneId, @Valid @RequestBody DeliveryZoneRequest request) {
        return zones.update(zoneId, request);
    }
}
