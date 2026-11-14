package lk.swiftdrop.tracking.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.CourierView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.EtaView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.FailureView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.HistoryView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.ParcelView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.PlaceView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.PointView;
import lk.swiftdrop.tracking.api.PublicTrackingResponse.StopView;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.CourierFix;
import lk.swiftdrop.tracking.domain.FailureReason;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.domain.Place;
import lk.swiftdrop.tracking.domain.TimelineEntry;
import lk.swiftdrop.tracking.eta.EtaService;
import lk.swiftdrop.tracking.eta.Geo;
import org.springframework.stereotype.Component;

/** Builds the public view of a parcel. The single place that decides what the tracking page may show. */
@Component
public class PublicTrackingMapper {

    /** The five stops of the transit line. */
    static final List<ParcelStatus> LINE = List.of(ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
            ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);

    private final EtaService etaService;
    private final TrackingProperties properties;

    public PublicTrackingMapper(EtaService etaService, TrackingProperties properties) {
        this.etaService = etaService;
        this.properties = properties;
    }

    public PublicTrackingResponse toResponse(ParcelTracking parcel, Instant now) {
        ParcelStatus internal = parcel.getStatus() != null ? parcel.getStatus() : ParcelStatus.CREATED;
        ParcelStatus status = internal.publicStatus();

        EtaView eta = etaService.estimate(parcel, now)
                .map(window -> new EtaView(window.from(), window.to(), window.basis().name()))
                .orElse(null);

        return new PublicTrackingResponse(
                parcel.getTrackingNo(),
                status.name(),
                status.label(),
                status.tone().name(),
                status.stop(),
                ParcelStatus.TOTAL_STOPS,
                status.terminal(),
                place(parcel.getOrigin()),
                place(parcel.getDestination()),
                parcel.getParcel() == null ? null : new ParcelView(parcel.getParcel().size(),
                        parcel.getParcel().weightKg(), parcel.getParcel().serviceType()),
                stops(parcel, status),
                history(parcel),
                eta,
                status == ParcelStatus.DELIVERED ? parcel.getStatusChangedAt() : null,
                failure(parcel, status),
                courier(parcel, internal, now),
                parcel.getUpdatedAt(),
                parcel.getVersion());
    }

    private static List<StopView> stops(ParcelTracking parcel, ParcelStatus status) {
        List<StopView> stops = new ArrayList<>(LINE.size());
        for (int i = 0; i < LINE.size(); i++) {
            int number = i + 1;
            ParcelStatus stopStatus = LINE.get(i);
            if (number < status.stop()) {
                stops.add(new StopView(number, stopStatus.name(), stopStatus.label(),
                        lastTime(parcel, stopStatus), "DONE"));
            } else if (number == status.stop()) {
                // An exception (FAILED, RESCHEDULED, RETURNED, CANCELLED) replaces the normal stop it happened at.
                ParcelStatus shown = status == stopStatus ? stopStatus : status;
                String state = switch (status.tone()) {
                    case DONE -> "DONE";
                    case EXCEPTION -> "EXCEPTION";
                    case ATTENTION -> "ATTENTION";
                    case PROGRESS -> "CURRENT";
                };
                stops.add(new StopView(number, shown.name(), shown.label(), lastTime(parcel, shown), state));
            } else {
                stops.add(new StopView(number, stopStatus.name(), stopStatus.label(), null, "PENDING"));
            }
        }
        return stops;
    }

    private static List<HistoryView> history(ParcelTracking parcel) {
        List<HistoryView> history = new ArrayList<>();
        List<TimelineEntry> timeline = parcel.getTimeline();
        for (int i = timeline.size() - 1; i >= 0; i--) {
            TimelineEntry entry = timeline.get(i);
            if (entry.status() == ParcelStatus.ASSIGNED) {
                continue;
            }
            String note = entry.reason() != null ? FailureReason.fromCode(entry.reason()).label() : null;
            history.add(new HistoryView(entry.status().name(), entry.status().label(), entry.at(),
                    entry.location() != null ? entry.location() : defaultLocation(parcel, entry.status()), note));
        }
        return history;
    }

    /** Without a hub name, show the city the step happened in when it is obvious. */
    private static String defaultLocation(ParcelTracking parcel, ParcelStatus status) {
        Place place = switch (status) {
            case PICKED_UP -> parcel.getOrigin();
            case OUT_FOR_DELIVERY, DELIVERED, FAILED -> parcel.getDestination();
            default -> null;
        };
        return place != null ? place.city() : null;
    }

    private static FailureView failure(ParcelTracking parcel, ParcelStatus status) {
        if (parcel.getFailureReason() == null || (status != ParcelStatus.FAILED
                && status != ParcelStatus.RESCHEDULED && status != ParcelStatus.RETURNED)) {
            return null;
        }
        FailureReason reason = FailureReason.fromCode(parcel.getFailureReason());
        return new FailureView(reason.name(), reason.label());
    }

    private CourierView courier(ParcelTracking parcel, ParcelStatus internal, Instant now) {
        CourierFix fix = parcel.getCourierPosition();
        if (fix == null || !properties.getShareLocationStatuses().contains(internal)
                || fix.at().isBefore(now.minus(properties.getLocationStaleAfter()))) {
            return null;
        }
        Double distance = null;
        if (parcel.getDestination() != null && parcel.getDestination().point() != null) {
            distance = BigDecimal.valueOf(Geo.distanceKm(fix.point(), parcel.getDestination().point()))
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
        List<PointView> trail = parcel.getCourierTrail().stream().map(PublicTrackingMapper::point).toList();
        return new CourierView(point(fix.point()), fix.at(), trail, distance);
    }

    private static Instant lastTime(ParcelTracking parcel, ParcelStatus status) {
        Instant last = null;
        for (TimelineEntry entry : parcel.getTimeline()) {
            if (entry.status() == status && (last == null || entry.at().isAfter(last))) {
                last = entry.at();
            }
        }
        return last;
    }

    private static PlaceView place(Place place) {
        if (place == null) {
            return null;
        }
        return new PlaceView(place.city(), place.point() == null ? null : point(place.point()));
    }

    private static PointView point(GeoPoint point) {
        return new PointView(point.lat(), point.lng());
    }
}
