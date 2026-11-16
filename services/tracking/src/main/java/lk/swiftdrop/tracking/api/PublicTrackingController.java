package lk.swiftdrop.tracking.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import lk.swiftdrop.tracking.common.ApiException;
import lk.swiftdrop.tracking.common.ErrorCode;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.domain.TrackingNumbers;
import lk.swiftdrop.tracking.push.TrackingStreamService;
import lk.swiftdrop.tracking.store.TrackingStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public tracking by number, no login. Both endpoints read the Redis read model only, so a burst of tracking
 * traffic never reaches the Parcel database.
 * <ul>
 *   <li>{@code GET /api/v1/public/tracking/{trackingNo}}: current state; also the polling fallback.</li>
 *   <li>{@code GET /api/v1/public/tracking/{trackingNo}/stream}: server-sent events, first the current state, then
 *       one {@code tracking} event per change.</li>
 * </ul>
 * {@code /api/v1/public/**} is the API gateway's public prefix (Member 1). The same endpoints also answer under
 * {@code /api/v1/tracking}, the prefix the gateway routes for signed-in users' live streams.
 */
@RestController
@RequestMapping({"/api/v1/public/tracking", "/api/v1/tracking"})
public class PublicTrackingController {

    private static final String NOT_FOUND_MESSAGE = "No parcel was found with this tracking number";

    private final TrackingStore store;
    private final PublicTrackingMapper mapper;
    private final TrackingStreamService streams;
    private final Clock clock;

    public PublicTrackingController(TrackingStore store, PublicTrackingMapper mapper, TrackingStreamService streams,
                                    Clock clock) {
        this.store = store;
        this.mapper = mapper;
        this.streams = streams;
        this.clock = clock;
    }

    @GetMapping(path = "/{trackingNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicTrackingResponse> track(@PathVariable String trackingNo) {
        ParcelTracking parcel = load(trackingNo);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(parcel, clock.instant()));
    }

    @GetMapping(path = "/{trackingNo}/stream")
    public SseEmitter stream(@PathVariable String trackingNo, HttpServletResponse response) {
        ParcelTracking parcel = load(trackingNo);
        // Tell nginx-style proxies not to buffer the stream.
        response.setHeader("X-Accel-Buffering", "no");
        return streams.open(parcel);
    }

    private ParcelTracking load(String rawTrackingNo) {
        String trackingNo = TrackingNumbers.normalize(rawTrackingNo)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRACKING_NUMBER,
                        "Tracking numbers contain only letters, digits and dashes"));
        return store.find(trackingNo)
                .orElseThrow(() -> new ApiException(ErrorCode.TRACKING_NOT_FOUND, NOT_FOUND_MESSAGE));
    }
}
