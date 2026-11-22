package lk.swiftdrop.audit.web;

import lk.swiftdrop.audit.service.AuditQueryService;
import lk.swiftdrop.audit.web.dto.AuditLogResponse;
import lk.swiftdrop.audit.web.dto.PageResponse;
import lk.swiftdrop.audit.web.dto.VerifyResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Read-only API for the admin console's Audit Log page (ADMIN only, enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditQueryService service;

    public AuditLogController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(new AuditQueryService.Filter(actor, action, entityType, entityId, from, to), page, size);
    }

    /** Re-computes the whole hash chain and reports whether the log is intact. */
    @GetMapping("/verify")
    public VerifyResponse verify() {
        return service.verify();
    }

    @GetMapping("/{id}")
    public AuditLogResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
