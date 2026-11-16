package lk.swiftdrop.document.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Which events each consumer has already handled; an event id seen twice is a duplicate delivery. */
@Repository
public class ProcessedEvents {
    private final JdbcClient jdbc;

    public ProcessedEvents(JdbcClient jdbc) { this.jdbc = jdbc; }

    public boolean exists(UUID eventId, String consumer) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id AND consumer = :consumer")
                .param("id", eventId).param("consumer", consumer).query(Long.class).single() > 0;
    }

    /** @return true if this call recorded the event, false if it was already recorded (a duplicate or a lost race) */
    public boolean markProcessed(UUID eventId, String consumer, Instant at) {
        return jdbc.sql("INSERT INTO processed_event (event_id, consumer, processed_at) VALUES (:id, :consumer, :at) "
                        + "ON CONFLICT DO NOTHING")
                .param("id", eventId).param("consumer", consumer).param("at", Timestamp.from(at)).update() == 1;
    }
}
