package com.swiftdrop.backend.dispatcher.service;

import com.swiftdrop.backend.dispatcher.dto.DeliveryZoneRequest;
import com.swiftdrop.backend.dispatcher.dto.DeliveryZoneResponse;
import com.swiftdrop.backend.dispatcher.entity.DeliveryZone;
import com.swiftdrop.backend.dispatcher.repository.DeliveryZoneRepository;
import com.swiftdrop.backend.shared.error.ConflictException;
import com.swiftdrop.backend.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delivery zones: named groups of districts. Rules: code 2 to 4 letters (stored in capitals) and unique; only the 25
 * Sri Lankan districts; each district in one zone at most. Zones don't change prices or bookings yet.
 */
@Service
@Transactional
public class DeliveryZoneService {

    /** Sri Lanka's 25 districts, by province (the order the admin page shows them in). */
    static final List<String> DISTRICTS = List.of(
            "Colombo", "Gampaha", "Kalutara",                              // Western
            "Kandy", "Matale", "Nuwara Eliya",                             // Central
            "Galle", "Matara", "Hambantota",                               // Southern
            "Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu",   // Northern
            "Batticaloa", "Ampara", "Trincomalee",                         // Eastern
            "Kurunegala", "Puttalam",                                      // North Western
            "Anuradhapura", "Polonnaruwa",                                 // North Central
            "Badulla", "Monaragala",                                       // Uva
            "Ratnapura", "Kegalle");                                       // Sabaragamuwa

    private static final Map<String, String> BY_LOWER_CASE = DISTRICTS.stream()
            .collect(Collectors.toMap(d -> d.toLowerCase(Locale.ROOT), Function.identity()));

    private static final long NEW_ZONE = -1L;

    private final DeliveryZoneRepository zones;

    DeliveryZoneService(DeliveryZoneRepository zones) {
        this.zones = zones;
    }

    /** Every zone by name. */
    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> list() {
        return zones.findAllByOrderByNameAscIdAsc().stream().map(DeliveryZoneService::response).toList();
    }

    public DeliveryZoneResponse create(DeliveryZoneRequest request) {
        Checked checked = check(request, NEW_ZONE);
        DeliveryZone zone = new DeliveryZone(checked.name(), checked.code(), checked.districts(), request.active());
        return response(zones.saveAndFlush(zone));
    }

    public DeliveryZoneResponse update(Long zoneId, DeliveryZoneRequest request) {
        DeliveryZone zone = zones.findById(zoneId).orElseThrow(() -> new NotFoundException("Zone not found"));
        Checked checked = check(request, zoneId);
        zone.update(checked.name(), checked.code(), checked.districts(), request.active());
        return response(zones.saveAndFlush(zone));
    }

    private record Checked(String name, String code, Set<String> districts) { }

    /**
     * 400 for an unknown district; 409 when another zone has the code or one of the districts. Two admins saving at
     * the same moment can both pass these checks: the UNIQUE code and district columns then refuse the second (409).
     */
    private Checked check(DeliveryZoneRequest request, long exceptId) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        Set<String> districts = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : request.districts()) {
            String district = raw == null ? null : BY_LOWER_CASE.get(raw.trim().toLowerCase(Locale.ROOT));
            if (district == null) unknown.add(raw == null ? "(empty)" : raw.trim());
            else districts.add(district);
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Not a Sri Lankan district: " + String.join(", ", unknown) + ".");
        }
        if (zones.existsByCodeAndIdNot(code, exceptId)) {
            throw new ConflictException("Another zone already uses the code " + code + ".");
        }
        List<Object[]> taken = zones.findTakenDistricts(districts, exceptId);
        if (!taken.isEmpty()) {
            String list = taken.stream().map(row -> row[0] + " (" + row[1] + ")").collect(Collectors.joining(", "));
            throw new ConflictException("Already in another zone: " + list + ".");
        }
        return new Checked(request.name().trim(), code, districts);
    }

    private static DeliveryZoneResponse response(DeliveryZone zone) {
        Set<String> mine = zone.getDistricts();
        List<String> ordered = DISTRICTS.stream().filter(mine::contains).toList();
        return new DeliveryZoneResponse(zone.getId(), zone.getName(), zone.getCode(), ordered, zone.isActive());
    }
}
