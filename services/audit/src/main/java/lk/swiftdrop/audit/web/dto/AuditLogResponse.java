package lk.swiftdrop.audit.web.dto;

import lk.swiftdrop.audit.domain.AuditLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        Long seq,
        UUID eventId,
        Instant occurredAt,
        Instant recordedAt,
        String actorId,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        String ipAddress,
        String correlationId,
        String producer,
        Map<String, String> details,
        String prevHash,
        String rowHash) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getSeq(), log.getEventId(), log.getOccurredAt(),
                log.getRecordedAt(), log.getActorId(), log.getActorRole(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getIpAddress(), log.getCorrelationId(), log.getProducer(), log.getDetails(),
                log.getPrevHash(), log.getRowHash());
    }
}
