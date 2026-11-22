package lk.swiftdrop.tracking.domain;

import java.time.Instant;

/**
 * One row of the parcel history.
 *
 * @param eventId  id of the event that produced the row (internal, used to ignore duplicates, never shown)
 * @param status   status reached
 * @param at       when it happened (event occurredAt)
 * @param location hub or city name, for example "Kandy hub" (may be null)
 * @param reason   failure reason code for FAILED rows (may be null)
 */
public record TimelineEntry(String eventId, ParcelStatus status, Instant at, String location, String reason) {
}
