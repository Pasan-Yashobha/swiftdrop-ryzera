package com.swiftdrop.reporting.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link ProcessedEvent}.
 */
public class ProcessedEventId implements Serializable {

    private UUID   eventId;
    private String consumer;

    public ProcessedEventId() {}

    public ProcessedEventId(UUID eventId, String consumer) {
        this.eventId  = eventId;
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessedEventId that)) return false;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumer);
    }
}
