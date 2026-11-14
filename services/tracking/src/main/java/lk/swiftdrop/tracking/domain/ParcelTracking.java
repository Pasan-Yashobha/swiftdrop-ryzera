package lk.swiftdrop.tracking.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The read model of one parcel, stored in Redis as JSON under {@code tracking:parcel:<trackingNo>}.
 * It holds only what the tracking page needs: status, history, city-level origin and destination, parcel size,
 * and the courier's live position while the parcel is on its way. Names, phone numbers and street addresses
 * never reach this object (see {@code EventMapper}), so they cannot leak from any endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParcelTracking {

    /** How many processed event ids are remembered per parcel to ignore duplicates. */
    public static final int RECENT_EVENT_IDS = 64;

    private String trackingNo;
    private String parcelId;
    private ParcelStatus status;
    private Instant statusChangedAt;
    private Instant lastEventAt;
    private Long lastSequence;
    private Place origin;
    private Place destination;
    private ParcelInfo parcel;
    /** Internal: needed to route courier.location.updated events; never exposed. */
    private String courierId;
    private CourierFix courierPosition;
    private List<GeoPoint> courierTrail = new ArrayList<>();
    private String failureReason;
    private List<TimelineEntry> timeline = new ArrayList<>();
    private List<String> recentEventIds = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public ParcelTracking() {
    }

    public ParcelTracking(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public boolean hasApplied(String eventId) {
        return eventId != null && recentEventIds.contains(eventId);
    }

    public void rememberEvent(String eventId) {
        if (eventId == null || recentEventIds.contains(eventId)) {
            return;
        }
        recentEventIds.add(eventId);
        while (recentEventIds.size() > RECENT_EVENT_IDS) {
            recentEventIds.remove(0);
        }
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getParcelId() {
        return parcelId;
    }

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

    public ParcelStatus getStatus() {
        return status;
    }

    public void setStatus(ParcelStatus status) {
        this.status = status;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(Instant statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(Long lastSequence) {
        this.lastSequence = lastSequence;
    }

    public Place getOrigin() {
        return origin;
    }

    public void setOrigin(Place origin) {
        this.origin = origin;
    }

    public Place getDestination() {
        return destination;
    }

    public void setDestination(Place destination) {
        this.destination = destination;
    }

    public ParcelInfo getParcel() {
        return parcel;
    }

    public void setParcel(ParcelInfo parcel) {
        this.parcel = parcel;
    }

    public String getCourierId() {
        return courierId;
    }

    public void setCourierId(String courierId) {
        this.courierId = courierId;
    }

    public CourierFix getCourierPosition() {
        return courierPosition;
    }

    public void setCourierPosition(CourierFix courierPosition) {
        this.courierPosition = courierPosition;
    }

    public List<GeoPoint> getCourierTrail() {
        return courierTrail;
    }

    public void setCourierTrail(List<GeoPoint> courierTrail) {
        this.courierTrail = courierTrail != null ? new ArrayList<>(courierTrail) : new ArrayList<>();
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<TimelineEntry> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineEntry> timeline) {
        this.timeline = timeline != null ? new ArrayList<>(timeline) : new ArrayList<>();
    }

    public List<String> getRecentEventIds() {
        return recentEventIds;
    }

    public void setRecentEventIds(List<String> recentEventIds) {
        this.recentEventIds = recentEventIds != null ? new ArrayList<>(recentEventIds) : new ArrayList<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
