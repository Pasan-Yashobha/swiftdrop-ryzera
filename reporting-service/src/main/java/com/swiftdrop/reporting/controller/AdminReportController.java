package com.swiftdrop.reporting.controller;

import com.swiftdrop.reporting.common.CsvExportHelper;
import com.swiftdrop.reporting.dto.*;
import com.swiftdrop.reporting.service.ReportQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin and Dispatcher report endpoints under /api/v1/admin/reports (brief §5).
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
public class AdminReportController {

    private final ReportQueryService reportQueryService;

    public AdminReportController(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String format) {

        SummaryResponse response = reportQueryService.getSummary(from, to);

        if ("csv".equalsIgnoreCase(format)) {
            List<String> headers = List.of(
                    "created", "delivered", "failed", "returned",
                    "attemptRate", "finalOutcomeRate", "activeCouriers",
                    "reconciliationMismatch", "lastUpdatedAt");
            List<Object> row = List.of(
                    response.created(), response.delivered(), response.failed(), response.returned(),
                    response.attemptRate() != null ? response.attemptRate() : "",
                    response.finalOutcomeRate() != null ? response.finalOutcomeRate() : "",
                    response.activeCouriers(), response.reconciliationMismatch(),
                    response.lastUpdatedAt() != null ? response.lastUpdatedAt() : "");
            String csv = CsvExportHelper.toCsv(headers, List.of(row));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=summary.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/deliveries-per-day")
    public ResponseEntity<?> getDeliveriesPerDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String format) {

        List<DailyPointResponse> list = reportQueryService.getDeliveriesPerDay(from, to, zoneId);

        if ("csv".equalsIgnoreCase(format)) {
            List<String> headers = List.of("date", "created", "delivered", "failed");
            List<List<Object>> rows = new ArrayList<>();
            for (DailyPointResponse p : list) {
                rows.add(List.of(p.date(), p.created(), p.delivered(), p.failed()));
            }
            String csv = CsvExportHelper.toCsv(headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=deliveries-per-day.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/success-rate")
    public ResponseEntity<?> getSuccessRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String format) {

        SuccessRateResponse response = reportQueryService.getSuccessRate(from, to);

        if ("csv".equalsIgnoreCase(format)) {
            List<String> headers = List.of("from", "to", "attemptRate", "finalOutcomeRate");
            List<Object> row = List.of(
                    response.from(), response.to(),
                    response.attemptRate() != null ? response.attemptRate() : "",
                    response.finalOutcomeRate() != null ? response.finalOutcomeRate() : "");
            String csv = CsvExportHelper.toCsv(headers, List.of(row));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=success-rate.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/courier-performance")
    public ResponseEntity<?> getCourierPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String format) {

        List<CourierPerformanceResponse> list = reportQueryService.getCourierPerformance(from, to, sort);

        if ("csv".equalsIgnoreCase(format)) {
            List<String> headers = List.of(
                    "courierId", "courierName", "assignedCount", "reassignedAwayCount",
                    "deliveredCount", "failedAttempts", "totalMinutes", "timedCount",
                    "averageDeliveryMinutes");
            List<List<Object>> rows = new ArrayList<>();
            for (CourierPerformanceResponse c : list) {
                rows.add(List.of(
                        c.courierId(), c.courierName(), c.assignedCount(), c.reassignedAwayCount(),
                        c.deliveredCount(), c.failedAttempts(), c.totalMinutes(), c.timedCount(),
                        c.averageDeliveryMinutes() != null ? c.averageDeliveryMinutes() : ""));
            }
            String csv = CsvExportHelper.toCsv(headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=courier-performance.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/zones")
    public ResponseEntity<?> getZones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String format) {

        List<ZoneStatsResponse> list = reportQueryService.getZoneStats(from, to);

        if ("csv".equalsIgnoreCase(format)) {
            List<String> headers = List.of("zoneId", "zoneName", "createdCount", "deliveredCount", "failedCount", "feeTotal");
            List<List<Object>> rows = new ArrayList<>();
            for (ZoneStatsResponse z : list) {
                rows.add(List.of(z.zoneId(), z.zoneName(), z.createdCount(), z.deliveredCount(), z.failedCount(), z.feeTotal()));
            }
            String csv = CsvExportHelper.toCsv(headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=zones.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        return ResponseEntity.ok(list);
    }
}
