package com.swiftdrop.backend.dispatcher.controller;

import com.swiftdrop.backend.dispatcher.dto.DeliveryReportResponse;
import com.swiftdrop.backend.dispatcher.service.DeliveryReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin's Reports page and the Admin Overview numbers. ADMIN only. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
class AdminReportController {

    private final DeliveryReportService reports;

    AdminReportController(DeliveryReportService reports) {
        this.reports = reports;
    }

    /** e.g. GET /api/admin/reports?days=7: the last 7 Sri Lanka days, today included (1 to 90; days=1 is today). */
    @GetMapping
    DeliveryReportResponse report(@RequestParam(defaultValue = "7") int days) {
        return reports.report(days);
    }
}
