package com.swiftdrop.backend.dispatcher.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Activity over the last {@code days} Sri Lanka days, today included ({@code from} to {@code to}, both inclusive).
 * Delivered counts by delivery date; failed attempts are FAILED tracking events.
 */
public record DeliveryReportResponse(
        LocalDate from,
        LocalDate to,
        long bookingsCreated,
        long delivered,
        long failedAttempts,
        List<Day> perDay,
        List<CourierRow> couriers) {

    /** One Sri Lanka day (every day of the period is present, oldest first). */
    public record Day(LocalDate day, long bookings, long delivered, long failedAttempts) { }

    /**
     * One courier profile: deliveries completed and failed attempts in the period, and active assignments now.
     * Every courier is listed, most deliveries first.
     */
    public record CourierRow(Long courierId, String name, long delivered, long failedAttempts, long active) { }
}
