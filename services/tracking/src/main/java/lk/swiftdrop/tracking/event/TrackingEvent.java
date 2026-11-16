package lk.swiftdrop.tracking.event;

import java.time.Instant;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelInfo;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.Place;

/**
 * A domain event reduced to the whitelisted, non-sensitive fields the Tracking Service is allowed to keep.
 *
 * @param eventId       envelope event id
 * @param kind          what happened
 * @param occurredAt    when it happened (for locations: when the position was recorded)
 * @param sequence      per-parcel sequence number from the producer, if it sends one
 * @param trackingNo    canonical tracking number (may be null when only parcelId is known)
 * @param parcelId      parcel id (may be null)
 * @param status        status reached (null for courier locations and plain reassignments)
 * @param courierId     courier id (internal only)
 * @param origin        city-level origin (parcel.created)
 * @param destination   city-level destination (parcel.created)
 * @param parcel        size, weight and service type (parcel.created)
 * @param hub           hub or city where the status change happened
 * @param failureReason {@link lk.swiftdrop.tracking.domain.FailureReason} code (FAILED only)
 * @param position      courier position (courier locations only)
 */
public record TrackingEvent(
        String eventId,
        Kind kind,
        Instant occurredAt,
        Long sequence,
        String trackingNo,
        String parcelId,
        ParcelStatus status,
        String courierId,
        Place origin,
        Place destination,
        ParcelInfo parcel,
        String hub,
        String failureReason,
        GeoPoint position) {

    /** The kinds of change the read model understands. */
    public enum Kind { PARCEL_CREATED, STATUS_CHANGED, ASSIGNED, REASSIGNED, CANCELLED, COURIER_LOCATION }

    public TrackingEvent withTrackingNo(String newTrackingNo) {
        return new TrackingEvent(eventId, kind, occurredAt, sequence, newTrackingNo, parcelId, status, courierId,
                origin, destination, parcel, hub, failureReason, position);
    }

    public static Builder builder(String eventId, Kind kind, Instant occurredAt) {
        return new Builder(eventId, kind, occurredAt);
    }

    /** Fluent builder; only eventId, kind and occurredAt are required. */
    public static final class Builder {

        private final String eventId;
        private final Kind kind;
        private final Instant occurredAt;
        private Long sequence;
        private String trackingNo;
        private String parcelId;
        private ParcelStatus status;
        private String courierId;
        private Place origin;
        private Place destination;
        private ParcelInfo parcel;
        private String hub;
        private String failureReason;
        private GeoPoint position;

        private Builder(String eventId, Kind kind, Instant occurredAt) {
            this.eventId = eventId;
            this.kind = kind;
            this.occurredAt = occurredAt;
        }

        public Builder sequence(Long value) {
            this.sequence = value;
            return this;
        }

        public Builder trackingNo(String value) {
            this.trackingNo = value;
            return this;
        }

        public Builder parcelId(String value) {
            this.parcelId = value;
            return this;
        }

        public Builder status(ParcelStatus value) {
            this.status = value;
            return this;
        }

        public Builder courierId(String value) {
            this.courierId = value;
            return this;
        }

        public Builder origin(Place value) {
            this.origin = value;
            return this;
        }

        public Builder destination(Place value) {
            this.destination = value;
            return this;
        }

        public Builder parcel(ParcelInfo value) {
            this.parcel = value;
            return this;
        }

        public Builder hub(String value) {
            this.hub = value;
            return this;
        }

        public Builder failureReason(String value) {
            this.failureReason = value;
            return this;
        }

        public Builder position(GeoPoint value) {
            this.position = value;
            return this;
        }

        public TrackingEvent build() {
            return new TrackingEvent(eventId, kind, occurredAt, sequence, trackingNo, parcelId, status, courierId,
                    origin, destination, parcel, hub, failureReason, position);
        }
    }
}
