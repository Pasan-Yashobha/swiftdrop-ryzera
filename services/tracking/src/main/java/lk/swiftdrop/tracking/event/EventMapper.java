package lk.swiftdrop.tracking.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lk.swiftdrop.tracking.domain.FailureReason;
import lk.swiftdrop.tracking.domain.GeoPoint;
import lk.swiftdrop.tracking.domain.ParcelInfo;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.Place;
import lk.swiftdrop.tracking.domain.TrackingNumbers;
import lk.swiftdrop.tracking.event.TrackingEvent.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps an envelope to a {@link TrackingEvent}. This class is the privacy boundary of the service: it reads a fixed
 * whitelist of fields (ids, status, city names, parcel size, coordinates) and nothing else, so recipient names,
 * phone numbers, e-mail addresses and street addresses that producers may include are never stored.
 * Field names are tolerant (for example {@code trackingNo} or {@code trackingNumber}) because the producers
 * (M4 Parcel, M6 Dispatch, M7 Courier) were built in parallel; see docs/tracking-service/CONTRACT.md.
 */
@Component
public class EventMapper {

    private static final Logger log = LoggerFactory.getLogger(EventMapper.class);

    private static final String STATUS_PREFIX = "parcel.status.";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Z][A-Z0-9_]{0,19}");
    private static final int MAX_NAME_LENGTH = 60;

    /**
     * Returns the tracking event, or empty for event types this service does not use.
     *
     * @throws InvalidEventException when a relevant event lacks the ids or coordinates it needs
     */
    public Optional<TrackingEvent> map(EventEnvelope envelope) {
        String type = envelope.eventType();
        if (type.startsWith("courier.location.")) {
            return Optional.of(mapLocation(envelope));
        }
        if (!type.startsWith("parcel.")) {
            return Optional.empty();
        }

        JsonNode data = envelope.data();
        Kind kind;
        ParcelStatus status;
        switch (type) {
            case "parcel.created" -> {
                kind = Kind.PARCEL_CREATED;
                status = ParcelStatus.CREATED;
            }
            case "parcel.assigned" -> {
                kind = Kind.ASSIGNED;
                status = ParcelStatus.ASSIGNED;
            }
            case "parcel.reassigned" -> {
                kind = Kind.REASSIGNED;
                status = ParcelStatus.fromToken(Json.text(data, "toStatus", "status")).orElse(null);
            }
            case "parcel.cancelled" -> {
                kind = Kind.CANCELLED;
                status = ParcelStatus.CANCELLED;
            }
            default -> {
                if (!type.startsWith(STATUS_PREFIX)) {
                    return Optional.empty();
                }
                Optional<ParcelStatus> parsed = ParcelStatus.fromToken(type.substring(STATUS_PREFIX.length()))
                        .or(() -> ParcelStatus.fromToken(Json.text(data, "toStatus", "status")));
                if (parsed.isEmpty()) {
                    log.warn("Ignoring {} with an unknown status (event {})", type, envelope.eventId());
                    return Optional.empty();
                }
                status = parsed.get();
                kind = switch (status) {
                    case CANCELLED -> Kind.CANCELLED;
                    case ASSIGNED -> Kind.ASSIGNED;
                    default -> Kind.STATUS_CHANGED;
                };
            }
        }

        String trackingNo = TrackingNumbers.normalize(Json.text(data, "trackingNo", "trackingNumber", "tracking_no"))
                .orElse(null);
        String parcelId = safeId(Json.text(data, "parcelId", "parcel_id", "parcel.id"));
        if (parcelId == null) {
            parcelId = safeId(envelope.aggregateId());
        }
        if (trackingNo == null && parcelId == null) {
            throw new InvalidEventException(type + " without trackingNo or parcelId");
        }

        TrackingEvent.Builder builder = TrackingEvent.builder(envelope.eventId(), kind, envelope.occurredAt())
                .sequence(Json.whole(data, "sequence", "seq", "sequenceNo"))
                .trackingNo(trackingNo)
                .parcelId(parcelId)
                .status(status)
                .courierId(safeId(Json.text(data, "courierId", "courier.id", "newCourierId", "toCourierId")))
                .hub(safeName(Json.text(data, "hub", "hubName", "facility", "facilityName")));

        if (kind == Kind.PARCEL_CREATED) {
            builder.origin(place(data, "pickup", "origin"))
                    .destination(place(data, "dropoff", "destination"))
                    .parcel(parcelInfo(data));
        }
        if (status == ParcelStatus.FAILED) {
            builder.failureReason(
                    FailureReason.fromCode(Json.text(data, "failureReason", "reason", "failureCode")).name());
        }
        return Optional.of(builder.build());
    }

    private TrackingEvent mapLocation(EventEnvelope envelope) {
        JsonNode data = envelope.data();
        GeoPoint point = GeoPoint.roundedOrNull(
                Json.number(data, "lat", "latitude", "location.lat", "location.latitude", "position.lat"),
                Json.number(data, "lng", "lon", "longitude", "location.lng", "location.lon", "location.longitude",
                        "position.lng"),
                GeoPoint.COURIER_PRECISION);
        if (point == null) {
            throw new InvalidEventException(envelope.eventType() + " without valid coordinates");
        }
        String courierId = safeId(Json.text(data, "courierId", "courier.id"));
        if (courierId == null) {
            courierId = safeId(envelope.aggregateId());
        }
        String trackingNo = TrackingNumbers.normalize(Json.text(data, "trackingNo", "trackingNumber")).orElse(null);
        String parcelId = safeId(Json.text(data, "parcelId"));
        if (courierId == null && trackingNo == null && parcelId == null) {
            throw new InvalidEventException(envelope.eventType() + " without courierId, trackingNo or parcelId");
        }
        Instant recordedAt = Json.instant(Json.text(data, "recordedAt", "at", "timestamp"));
        return TrackingEvent.builder(envelope.eventId(), Kind.COURIER_LOCATION,
                        recordedAt != null ? recordedAt : envelope.occurredAt())
                .courierId(courierId)
                .trackingNo(trackingNo)
                .parcelId(parcelId)
                .position(point)
                .build();
    }

    private static Place place(JsonNode data, String prefix, String alternative) {
        String city = safeName(Json.text(data,
                prefix + "City", prefix + ".city", alternative + "City", alternative + ".city",
                prefix + "ZoneName", prefix + "Zone.name", alternative + "ZoneName",
                prefix + "District", prefix + ".district"));
        GeoPoint point = GeoPoint.roundedOrNull(
                Json.number(data, prefix + "Lat", prefix + ".lat", prefix + ".latitude",
                        alternative + "Lat", alternative + ".lat", alternative + ".latitude"),
                Json.number(data, prefix + "Lng", prefix + ".lng", prefix + ".lon", prefix + ".longitude",
                        alternative + "Lng", alternative + ".lng", alternative + ".lon", alternative + ".longitude"),
                GeoPoint.CITY_PRECISION);
        return city == null && point == null ? null : new Place(city, point);
    }

    private static ParcelInfo parcelInfo(JsonNode data) {
        String size = safeToken(Json.text(data, "size", "parcelSize", "parcel.size"));
        BigDecimal weight = Json.decimal(data, "weightKg", "weight", "parcel.weightKg", "parcel.weight");
        if (weight != null && (weight.signum() <= 0 || weight.compareTo(BigDecimal.valueOf(1000)) > 0)) {
            weight = null;
        }
        if (weight != null) {
            weight = weight.setScale(2, RoundingMode.HALF_UP);
        }
        String service = safeToken(Json.text(data, "serviceType", "service", "deliveryType", "parcel.serviceType"));
        return size == null && weight == null && service == null ? null : new ParcelInfo(size, weight, service);
    }

    static String safeId(String value) {
        return value != null && SAFE_ID.matcher(value).matches() ? value : null;
    }

    static String safeToken(String value) {
        if (value == null) {
            return null;
        }
        String token = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return SAFE_TOKEN.matcher(token).matches() ? token : null;
    }

    /**
     * A place or hub name: printable, at most 60 characters, no '@' and fewer than five digits, so an e-mail
     * address or phone number put in the wrong field is dropped instead of displayed.
     */
    static String safeName(String value) {
        if (value == null) {
            return null;
        }
        String name = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.indexOf('@') >= 0) {
            return null;
        }
        long digits = name.chars().filter(Character::isDigit).count();
        return digits >= 5 ? null : name;
    }
}
