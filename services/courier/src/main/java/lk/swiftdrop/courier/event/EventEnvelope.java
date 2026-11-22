package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String eventType,
        Integer version,
        String occurredAt,
        String producer,
        String correlationId,
        String aggregateId,
        Map<String, Object> data,
        Map<String, Object> payload) {

    public Map<String, Object> payloadData() {
        if (data != null && !data.isEmpty()) {
            return data;
        }
        if (payload != null && !payload.isEmpty()) {
            return payload;
        }
        return Map.of();
    }
}
