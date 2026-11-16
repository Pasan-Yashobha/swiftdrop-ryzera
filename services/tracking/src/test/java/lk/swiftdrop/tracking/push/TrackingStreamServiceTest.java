package lk.swiftdrop.tracking.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lk.swiftdrop.tracking.api.PublicTrackingMapper;
import lk.swiftdrop.tracking.common.ApiException;
import lk.swiftdrop.tracking.common.ErrorCode;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import lk.swiftdrop.tracking.eta.EtaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class TrackingStreamServiceTest {

    private final TrackingProperties properties = new TrackingProperties();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private TrackingStreamService streams;

    @BeforeEach
    void setUp() {
        properties.getSse().setMaxConnections(3);
        properties.getSse().setMaxConnectionsPerParcel(2);
        streams = new TrackingStreamService(new PublicTrackingMapper(new EtaService(List.of()), properties),
                properties, Clock.fixed(Instant.parse("2026-11-13T06:00:00Z"), ZoneOffset.UTC), meters);
        streams.start();
    }

    private static ParcelTracking parcel(String trackingNo) {
        ParcelTracking parcel = new ParcelTracking(trackingNo);
        parcel.setStatus(ParcelStatus.IN_TRANSIT);
        return parcel;
    }

    @Test
    void streamsAreCountedPerParcelAndInTotal() {
        SseEmitter emitter = streams.open(parcel("SD-1000-0001"));
        streams.open(parcel("SD-1000-0001"));

        assertThat(emitter).isNotNull();
        assertThat(streams.openStreams()).isEqualTo(2);
        assertThat(streams.openStreams("SD-1000-0001")).isEqualTo(2);
        assertThat(streams.openStreams("SD-1000-0002")).isZero();
        assertThat(meters.get("tracking.sse.connections").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void thePerParcelLimitIsEnforced() {
        streams.open(parcel("SD-1000-0001"));
        streams.open(parcel("SD-1000-0001"));

        assertThatThrownBy(() -> streams.open(parcel("SD-1000-0001")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.STREAM_LIMIT_REACHED));
        assertThat(streams.openStreams()).isEqualTo(2);
    }

    @Test
    void theInstanceLimitIsEnforced() {
        streams.open(parcel("SD-1000-0001"));
        streams.open(parcel("SD-1000-0002"));
        streams.open(parcel("SD-1000-0003"));

        assertThatThrownBy(() -> streams.open(parcel("SD-1000-0004"))).isInstanceOf(ApiException.class);
    }

    @Test
    void pushingAndHeartbeatsDoNotFailWithOrWithoutListeners() {
        streams.push(parcel("SD-1000-0009"));
        streams.open(parcel("SD-1000-0001"));

        streams.push(parcel("SD-1000-0001"));
        streams.heartbeat();

        assertThat(streams.openStreams("SD-1000-0001")).isEqualTo(1);
    }

    @Test
    void stoppingClosesEveryStream() {
        streams.open(parcel("SD-1000-0001"));
        streams.open(parcel("SD-1000-0002"));

        streams.stop();

        assertThat(streams.isRunning()).isFalse();
        assertThat(streams.openStreams()).isZero();
        assertThat(streams.openStreams("SD-1000-0001")).isZero();
    }
}
