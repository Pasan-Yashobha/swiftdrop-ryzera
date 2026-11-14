package lk.swiftdrop.tracking.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What anyone holding a tracking number may see: status, five-stop transit line, history, ETA, city-level origin
 * and destination, parcel size and, while it is on its way, the courier's approximate position.
 * There is deliberately no field for names, phone numbers, e-mail addresses, street addresses or internal ids.
 * Used for both {@code GET /api/v1/tracking/{trackingNo}} and every SSE {@code tracking} event.
 *
 * @param status      public status (ASSIGNED is shown as CREATED)
 * @param tone        PROGRESS, ATTENTION, DONE or EXCEPTION
 * @param stop        current stop on the transit line, 1..totalStops
 * @param terminal    true when no further change is expected
 * @param stops       the transit line, always {@code totalStops} entries
 * @param history     newest first
 * @param eta         expected delivery window, null when not applicable
 * @param deliveredAt set once DELIVERED
 * @param failure     reason of the last failed attempt (FAILED, RESCHEDULED, RETURNED)
 * @param courier     live position while the parcel is on its way, otherwise null
 * @param version     increases with every change; also the SSE event id
 */
public record PublicTrackingResponse(
        String trackingNo,
        String status,
        String statusLabel,
        String tone,
        int stop,
        int totalStops,
        boolean terminal,
        PlaceView origin,
        PlaceView destination,
        ParcelView parcel,
        List<StopView> stops,
        List<HistoryView> history,
        EtaView eta,
        Instant deliveredAt,
        FailureView failure,
        CourierView courier,
        Instant lastUpdatedAt,
        long version) {

    /** City-level place; point rounded to about 1 km. */
    public record PlaceView(String city, PointView point) {
    }

    public record PointView(double lat, double lng) {
    }

    public record ParcelView(String size, BigDecimal weightKg, String serviceType) {
    }

    /**
     * One stop of the transit line.
     *
     * @param state DONE, CURRENT, ATTENTION, EXCEPTION or PENDING
     */
    public record StopView(int stop, String status, String label, Instant at, String state) {
    }

    public record HistoryView(String status, String label, Instant at, String location, String note) {
    }

    /** @param basis GPS (live courier position) or SCHEDULE (typical stage durations) */
    public record EtaView(Instant from, Instant to, String basis) {
    }

    public record FailureView(String code, String label) {
    }

    /**
     * @param position   last reported position, rounded to about 100 m
     * @param trail      recent positions, oldest first
     * @param distanceKm straight-line distance to the destination city, when known
     */
    public record CourierView(PointView position, Instant at, List<PointView> trail, Double distanceKm) {
    }
}
