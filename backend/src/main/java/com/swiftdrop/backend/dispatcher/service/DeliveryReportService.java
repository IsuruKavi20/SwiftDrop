package com.swiftdrop.backend.dispatcher.service;

import com.swiftdrop.backend.dispatcher.dto.AssignmentResponse;
import com.swiftdrop.backend.dispatcher.dto.DeliveryReportResponse;
import com.swiftdrop.backend.dispatcher.dto.DeliveryReportResponse.CourierRow;
import com.swiftdrop.backend.dispatcher.dto.DeliveryReportResponse.Day;
import com.swiftdrop.backend.dispatcher.entity.Courier;
import com.swiftdrop.backend.dispatcher.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.dispatcher.repository.CourierRepository;
import com.swiftdrop.backend.dispatcher.repository.DispatcherDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reports and Admin Overview numbers. Days are Sri Lanka days (Asia/Colombo), not UTC: a parcel delivered at 01:00
 * in Colombo counts for that day. Delivered counts by delivery date; failed attempts are FAILED tracking events,
 * credited to the courier who recorded them, or else to the delivery's current courier.
 */
@Service
@Transactional(readOnly = true)
public class DeliveryReportService {

    public static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");
    public static final int MAX_DAYS = 90;

    private final DispatcherDeliveryRepository deliveries;
    private final CourierRepository couriers;
    private final CourierAssignmentRepository assignments;
    private final Clock clock;

    DeliveryReportService(DispatcherDeliveryRepository deliveries, CourierRepository couriers,
                          CourierAssignmentRepository assignments, Clock clock) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.assignments = assignments;
        this.clock = clock;
    }

    /** The last {@code days} days, today included (1 = today only). 400 outside 1 to 90. */
    public DeliveryReportResponse report(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Choose between 1 and " + MAX_DAYS + " days.");
        }
        LocalDate today = LocalDate.now(clock.withZone(SRI_LANKA));
        LocalDate first = today.minusDays(days - 1L);
        Instant from = first.atStartOfDay(SRI_LANKA).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(SRI_LANKA).toInstant();

        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();   // [bookings, delivered, failed attempts]
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            perDay.put(day, new long[3]);
        }

        List<Courier> profiles = couriers.findAll();
        Map<UUID, Long> profileOf = new HashMap<>();
        Map<Long, long[]> perCourier = new HashMap<>();           // [delivered, failed attempts]
        for (Courier courier : profiles) {
            profileOf.put(courier.getUser().getId(), courier.getId());
            perCourier.put(courier.getId(), new long[2]);
        }

        long bookings = 0;
        for (Instant created : deliveries.findCreatedTimes(from, to)) {
            bookings++;
            bump(perDay, created, 0);
        }
        long delivered = 0;
        for (Object[] row : deliveries.findDeliveredInPeriod(from, to)) {
            delivered++;
            bump(perDay, (Instant) row[0], 1);
            Long courierId = row[1] == null ? null : profileOf.get((UUID) row[1]);
            if (courierId != null) perCourier.get(courierId)[0]++;
        }
        long failed = 0;
        for (Object[] row : deliveries.findFailedAttemptsInPeriod(from, to)) {
            failed++;
            bump(perDay, (Instant) row[0], 2);
            Long courierId = row[1] == null ? null : profileOf.get((UUID) row[1]);
            if (courierId == null && row[2] != null) courierId = profileOf.get((UUID) row[2]);
            if (courierId != null) perCourier.get(courierId)[1]++;
        }

        Map<Long, Long> active = new HashMap<>();
        assignments.countActiveByCourier().forEach(count -> active.put(count.getCourierId(), count.getTotal()));
        List<CourierRow> courierRows = profiles.stream()
                .map(c -> new CourierRow(c.getId(), AssignmentResponse.nameOf(c.getUser()), perCourier.get(c.getId())[0],
                        perCourier.get(c.getId())[1], active.getOrDefault(c.getId(), 0L)))
                .sorted(Comparator.comparingLong(CourierRow::delivered).reversed()
                        .thenComparing(CourierRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Day> dayRows = perDay.entrySet().stream()
                .map(e -> new Day(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
        return new DeliveryReportResponse(first, today, bookings, delivered, failed, dayRows, courierRows);
    }

    private static void bump(Map<LocalDate, long[]> perDay, Instant when, int column) {
        long[] counts = perDay.get(LocalDate.ofInstant(when, SRI_LANKA));
        if (counts != null) counts[column]++;
    }
}
