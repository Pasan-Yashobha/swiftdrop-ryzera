package lk.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.repository.CourierWorkloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourierAvailabilityConsumerTest {

    private CourierWorkloadRepository workloadRepository;
    private SseEmitterService sseEmitterService;
    private CourierAvailabilityConsumer consumer;

    private final UUID courierId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workloadRepository = mock(CourierWorkloadRepository.class);
        sseEmitterService = mock(SseEmitterService.class);
        consumer = new CourierAvailabilityConsumer(workloadRepository, sseEmitterService, new ObjectMapper());
    }

    @Test
    @DisplayName("Successfully consumes availability event and updates courier status")
    void updatesWorkloadOnEvent() {
        CourierWorkload existing = new CourierWorkload(courierId, null, 2, 8, false, null);
        when(workloadRepository.findByIdForUpdate(courierId)).thenReturn(Optional.of(existing));
        when(workloadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String json = """
                {
                    "eventType": "courier.availability.changed",
                    "data": {
                        "courierId": "%s",
                        "available": true,
                        "activeDeliveries": 1
                    }
                }
                """.formatted(courierId);

        consumer.handleCourierAvailabilityChanged(json);

        ArgumentCaptor<CourierWorkload> captor = ArgumentCaptor.forClass(CourierWorkload.class);
        verify(workloadRepository).save(captor.capture());

        CourierWorkload saved = captor.getValue();
        assertThat(saved.isAvailable()).isTrue();
        assertThat(saved.getActiveDeliveries()).isEqualTo(1);
        verify(sseEmitterService).broadcast(eq("workload.changed"), any());
    }

    @Test
    @DisplayName("Stale event older than lastAssignedAt never lowers the active deliveries count")
    void staleEventDoesNotLowerActiveDeliveries() {
        java.time.Instant assignmentTime = java.time.Instant.parse("2026-11-13T10:00:00Z");
        CourierWorkload existing = new CourierWorkload(courierId, null, 3, 8, true, assignmentTime);
        when(workloadRepository.findByIdForUpdate(courierId)).thenReturn(Optional.of(existing));
        when(workloadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Event occurred before last assignment
        String json = """
                {
                    "eventType": "courier.availability.changed",
                    "occurredAt": "2026-11-13T09:55:00Z",
                    "data": {
                        "courierId": "%s",
                        "available": true,
                        "activeDeliveries": 1
                    }
                }
                """.formatted(courierId);

        consumer.handleCourierAvailabilityChanged(json);

        ArgumentCaptor<CourierWorkload> captor = ArgumentCaptor.forClass(CourierWorkload.class);
        verify(workloadRepository).save(captor.capture());

        CourierWorkload saved = captor.getValue();
        // Count remains 3 because event occurred before lastAssignedAt
        assertThat(saved.getActiveDeliveries()).isEqualTo(3);
    }
}

