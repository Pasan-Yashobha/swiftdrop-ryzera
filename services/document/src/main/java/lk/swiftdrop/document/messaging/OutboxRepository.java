package lk.swiftdrop.document.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {
    private static final int MAX_ERROR = 300;
    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record Pending(UUID id, UUID eventId, String eventType, String payload, int attempts) { }

    public void insert(UUID id, UUID eventId, String eventType, String aggregateId, String payload, Instant createdAt) {
        jdbc.sql("""
                INSERT INTO outbox_event (id, event_id, event_type, aggregate_id, payload, created_at)
                VALUES (:id, :eventId, :type, :aggregate, :payload, :created)
                """)
                .param("id", id).param("eventId", eventId).param("type", eventType).param("aggregate", aggregateId)
                .param("payload", payload).param("created", Timestamp.from(createdAt)).update();
    }

    /** Oldest first, so events leave in the order they were written. */
    public List<Pending> findPending(int limit) {
        return jdbc.sql("SELECT id, event_id, event_type, payload, attempts FROM outbox_event "
                        + "WHERE published_at IS NULL ORDER BY created_at, id LIMIT :limit")
                .param("limit", limit)
                .query((row, index) -> new Pending(row.getObject("id", UUID.class), row.getObject("event_id", UUID.class),
                        row.getString("event_type"), row.getString("payload"), row.getInt("attempts"))).list();
    }

    public void markPublished(UUID id, Instant publishedAt) {
        jdbc.sql("UPDATE outbox_event SET published_at = :at WHERE id = :id")
                .param("at", Timestamp.from(publishedAt)).param("id", id).update();
    }

    /** Counts the failed attempt and keeps a short reason. The payload is never stored in the error. */
    public void recordFailure(UUID id, String error) {
        String reason = error == null ? "" : error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
        jdbc.sql("UPDATE outbox_event SET attempts = attempts + 1, last_error = :error WHERE id = :id")
                .param("error", reason).param("id", id).update();
    }
}
