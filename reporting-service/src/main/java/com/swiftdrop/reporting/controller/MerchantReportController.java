package com.swiftdrop.reporting.controller;

import com.swiftdrop.reporting.common.BadRequestException;
import com.swiftdrop.reporting.common.CsvExportHelper;
import com.swiftdrop.reporting.common.ErrorCode;
import com.swiftdrop.reporting.common.ForbiddenException;
import com.swiftdrop.reporting.dto.MerchantOrdersResponse;
import com.swiftdrop.reporting.service.ReportQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Merchant report endpoints under /api/v1/merchant/reports (brief §5).
 * Only accesses data for the caller's own merchant ID extracted from the JWT token.
 */
@RestController
@RequestMapping("/api/v1/merchant/reports")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantReportController {

    private final ReportQueryService reportQueryService;

    public MerchantReportController(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String format,
            @AuthenticationPrincipal Jwt jwt) {

        UUID merchantId = extractMerchantId(jwt);
        MerchantOrdersResponse response = reportQueryService.getMerchantOrders(from, to, merchantId);

        if ("csv".equalsIgnoreCase(format)) {
            List<String> headers = List.of("date", "merchantId", "merchantName", "createdCount", "deliveredCount", "failedCount");
            List<List<Object>> rows = new ArrayList<>();
            for (MerchantOrdersResponse.MerchantDayStat day : response.dailyStats()) {
                rows.add(List.of(
                        day.date(), response.merchantId(), response.merchantName(),
                        day.createdCount(), day.deliveredCount(), day.failedCount()
                ));
            }
            String csv = CsvExportHelper.toCsv(headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=merchant-orders.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        return ResponseEntity.ok(response);
    }

    private UUID extractMerchantId(Jwt jwt) {
        if (jwt == null) {
            throw new ForbiddenException("Authentication token is required");
        }

        String merchantIdStr = jwt.getClaimAsString("merchant_id");
        if (merchantIdStr == null || merchantIdStr.isBlank()) {
            merchantIdStr = jwt.getClaimAsString("merchantId");
        }
        if (merchantIdStr == null || merchantIdStr.isBlank()) {
            merchantIdStr = jwt.getSubject();
        }

        try {
            return UUID.fromString(merchantIdStr);
        } catch (Exception e) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR,
                    "Invalid merchant ID claim in token: " + merchantIdStr);
        }
    }
}
