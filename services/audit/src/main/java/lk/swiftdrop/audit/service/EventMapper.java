package lk.swiftdrop.audit.service;

import lk.swiftdrop.audit.domain.AuditEntry;
import lk.swiftdrop.audit.messaging.EventEnvelope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns any SwiftDrop event into an audit entry with one generic rule, so a new event type needs no code:
 * {@code merchant.approved} becomes action MERCHANT_APPROVED about entity type MERCHANT.
 * Who did it comes from the event data (first of: actorId, decidedBy, adminId, performedBy, changedBy, userId, ownerUserId).
 */
@Component
public class EventMapper {

    private static final List<String> ACTOR_KEYS =
            List.of("actorId", "decidedBy", "adminId", "performedBy", "changedBy", "userId", "ownerUserId");

    private final DetailsSanitizer sanitizer;

    public EventMapper(DetailsSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public AuditEntry map(EventEnvelope event) {
        if (event == null || !StringUtils.hasText(event.eventType())) {
            throw new InvalidEventException("The event has no eventType");
        }
        if (!StringUtils.hasText(event.eventId())) {
            throw new InvalidEventException("The event has no eventId");
        }
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        String type = event.eventType().trim();
        String entityType = type.contains(".") ? type.substring(0, type.indexOf('.')) : type;

        return new AuditEntry(
                toUuid(event.eventId()),
                // the database keeps microseconds, so keep exactly that (the hash must match after a reload)
                (event.occurredAt() == null ? Instant.now() : event.occurredAt()).truncatedTo(ChronoUnit.MICROS),
                limit(firstText(data), 100),
                limit(text(data.get("actorRole")), 30),
                limit(type.toUpperCase(Locale.ROOT).replace('.', '_'), 100),
                limit(entityType.toUpperCase(Locale.ROOT), 50),
                limit(StringUtils.hasText(event.aggregateId()) ? event.aggregateId() : text(data.get("entityId")), 100),
                limit(text(data.get("ipAddress")), 64),
                limit(event.correlationId(), 100),
                limit(event.producer(), 100),
                sanitizer.sanitize(data));
    }

    private static UUID toUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            // not a UUID: derive a stable one, so the same event id always maps to the same record
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String firstText(Map<String, Object> data) {
        for (String key : ACTOR_KEYS) {
            String value = text(data.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
