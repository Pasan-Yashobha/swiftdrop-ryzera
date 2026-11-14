package lk.swiftdrop.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** An event translated into what the audit log stores (before hashing). */
public record AuditEntry(
        UUID eventId,
        Instant occurredAt,
        String actorId,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        String ipAddress,
        String correlationId,
        String producer,
        Map<String, String> details) {
}
