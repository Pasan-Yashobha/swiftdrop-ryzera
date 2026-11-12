package com.swiftdrop.reporting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Incoming event envelope from RabbitMQ (brief §2).
 *
 * <pre>
 * {
 *   "eventId":       "uuid",
 *   "eventType":     "parcel.status.delivered",
 *   "occurredAt":    "2026-11-05T09:40:00Z",
 *   "correlationId": "uuid",
 *   "payload":       { ... }
 * }
 * </pre>
 */
public record EventEnvelope(
        @JsonProperty("eventId")       UUID                eventId,
        @JsonProperty("eventType")     String              eventType,
        @JsonProperty("occurredAt")    Instant             occurredAt,
        @JsonProperty("correlationId") String              correlationId,
        @JsonProperty("payload")       Map<String, Object> payload
) {
    /** True when the envelope is considered valid for processing. */
    public boolean isValid() {
        return eventId != null
            && eventType != null && !eventType.isBlank()
            && occurredAt != null;
    }
}
