package lk.swiftdrop.courier.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lk.swiftdrop.courier.common.*;
import lk.swiftdrop.courier.domain.*;
import lk.swiftdrop.courier.dto.LocationReportRequest;
import lk.swiftdrop.courier.event.LocationEventPublisher;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierLocationServiceTest {
    final Instant now = Instant.parse("2026-11-18T00:00:00Z");
    final UUID id = UUID.randomUUID();
    final CourierProfileRepository profiles = mock(CourierProfileRepository.class);
    final LocationEventPublisher publisher = mock(LocationEventPublisher.class);
    final CourierLocationService service = new CourierLocationService(profiles, publisher, Clock.fixed(now, ZoneOffset.UTC));
    final CourierProfile profile = new CourierProfile(id, "Test", "", VehicleType.BIKE, "", true, 0, 8, null, now);
    final LocationReportRequest request = new LocationReportRequest(BigDecimal.ONE, BigDecimal.TEN, now);

    @Test void recordsSuccessfulReportAndBlocksImmediateDuplicate() {
        when(profiles.findForLocationUpdate(id)).thenReturn(Optional.of(profile));
        service.report(id, request);
        assertThat(profile.getLastLocationReportAt()).isEqualTo(now);
        assertThatThrownBy(() -> service.report(id, request)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException)e).code()).isEqualTo(ErrorCode.RATE_LIMITED);
        verify(publisher, times(1)).publish(id, request, now);
    }
    @Test void permitsReportAtTenSecondBoundary() {
        profile.recordLocationReport(now.minusSeconds(10));
        when(profiles.findForLocationUpdate(id)).thenReturn(Optional.of(profile));
        service.report(id, request); verify(publisher).publish(id, request, now);
    }
    @Test void missingProfileDoesNotPublish() {
        when(profiles.findForLocationUpdate(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report(id, request)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(publisher);
    }
    @Test void failedPublishDoesNotRecordSuccess() {
        when(profiles.findForLocationUpdate(id)).thenReturn(Optional.of(profile));
        doThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "failed")).when(publisher).publish(id, request, now);
        assertThatThrownBy(() -> service.report(id, request)).isInstanceOf(ApiException.class);
        assertThat(profile.getLastLocationReportAt()).isNull();
    }
}
