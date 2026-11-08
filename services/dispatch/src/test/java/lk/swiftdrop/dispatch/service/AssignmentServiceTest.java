package lk.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.dispatch.client.ParcelClient;
import lk.swiftdrop.dispatch.common.BadRequestException;
import lk.swiftdrop.dispatch.common.ConflictException;
import lk.swiftdrop.dispatch.common.NotFoundException;
import lk.swiftdrop.dispatch.common.UnprocessableEntityException;
import lk.swiftdrop.dispatch.domain.Assignment;
import lk.swiftdrop.dispatch.domain.AssignmentStatus;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.OutboxEvent;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.AssignmentResponse;
import lk.swiftdrop.dispatch.dto.AutoAssignResponse;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import lk.swiftdrop.dispatch.repository.AssignmentRepository;
import lk.swiftdrop.dispatch.repository.CourierWorkloadRepository;
import lk.swiftdrop.dispatch.repository.OutboxEventRepository;
import lk.swiftdrop.dispatch.strategy.AssignmentStrategy;
import lk.swiftdrop.dispatch.strategy.AssignmentStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentServiceTest {

    private AssignmentRepository assignmentRepository;
    private CourierWorkloadRepository workloadRepository;
    private OutboxEventRepository outboxRepository;
    private AssignmentStrategyFactory strategyFactory;
    private ParcelClient parcelClient;
    private SseEmitterService sseEmitterService;
    private PlatformTransactionManager transactionManager;
    private AssignmentService assignmentService;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final UUID dispatcherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(AssignmentRepository.class);
        workloadRepository = mock(CourierWorkloadRepository.class);
        outboxRepository = mock(OutboxEventRepository.class);
        strategyFactory = mock(AssignmentStrategyFactory.class);
        parcelClient = mock(ParcelClient.class);
        sseEmitterService = mock(SseEmitterService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assignmentService = new AssignmentService(
                assignmentRepository,
                workloadRepository,
                outboxRepository,
                strategyFactory,
                parcelClient,
                sseEmitterService,
                new ObjectMapper(),
                transactionManager
        );
    }

    @Test
    @DisplayName("Manual assignment successfully saves assignment, increments courier load, and writes outbox event")
    void manualAssignSuccess() {
        CourierWorkload workload = new CourierWorkload(courierId, null, 2, 8, true, null);
        when(assignmentRepository.existsByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE)).thenReturn(false);
        when(workloadRepository.findByIdForUpdate(courierId)).thenReturn(Optional.of(workload));
        when(workloadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentResponse response = assignmentService.assignManual(parcelId, courierId, dispatcherId);

        assertThat(response.parcelId()).isEqualTo(parcelId);
        assertThat(response.courierId()).isEqualTo(courierId);
        assertThat(response.status()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(response.strategy()).isEqualTo(StrategyType.MANUAL);
        assertThat(workload.getActiveDeliveries()).isEqualTo(3);

        verify(assignmentRepository).save(any(Assignment.class));
        verify(workloadRepository).save(workload);
        verify(outboxRepository).save(any(OutboxEvent.class));
        verify(sseEmitterService).broadcast(any(), any());
    }

    @Test
    @DisplayName("Manual assignment throws 409 Conflict when parcel is already assigned")
    void manualAssignConflictOnAlreadyAssigned() {
        when(assignmentRepository.existsByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> assignmentService.assignManual(parcelId, courierId, dispatcherId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    @DisplayName("Manual assignment throws 422 when courier is unavailable")
    void manualAssignFailsWhenCourierOffline() {
        CourierWorkload offlineWorkload = new CourierWorkload(courierId, null, 0, 8, false, null);
        when(assignmentRepository.existsByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE)).thenReturn(false);
        when(workloadRepository.findByIdForUpdate(courierId)).thenReturn(Optional.of(offlineWorkload));

        assertThatThrownBy(() -> assignmentService.assignManual(parcelId, courierId, dispatcherId))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("offline/unavailable");
    }

    @Test
    @DisplayName("Manual assignment throws 422 when courier is at maximum capacity")
    void manualAssignFailsWhenCourierCapacityReached() {
        CourierWorkload fullWorkload = new CourierWorkload(courierId, null, 8, 8, true, null);
        when(assignmentRepository.existsByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE)).thenReturn(false);
        when(workloadRepository.findByIdForUpdate(courierId)).thenReturn(Optional.of(fullWorkload));

        assertThatThrownBy(() -> assignmentService.assignManual(parcelId, courierId, dispatcherId))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    @DisplayName("Reassignment successfully decrements old courier, increments new courier, and marks old assignment REASSIGNED")
    void reassignSuccess() {
        UUID oldCourierId = UUID.randomUUID();
        UUID newCourierId = UUID.randomUUID();
        Assignment currentAssignment = new Assignment(UUID.randomUUID(), parcelId, oldCourierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, dispatcherId, Instant.now().minusSeconds(100), null);

        CourierWorkload oldWorkload = new CourierWorkload(oldCourierId, null, 3, 8, true, null);
        CourierWorkload newWorkload = new CourierWorkload(newCourierId, null, 1, 8, true, null);

        when(assignmentRepository.findByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(currentAssignment));
        when(workloadRepository.findByIdForUpdate(newCourierId)).thenReturn(Optional.of(newWorkload));
        when(workloadRepository.findByIdForUpdate(oldCourierId)).thenReturn(Optional.of(oldWorkload));
        when(workloadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentResponse response = assignmentService.reassign(parcelId, newCourierId, "Courier vehicle breakdown", dispatcherId);

        assertThat(response.parcelId()).isEqualTo(parcelId);
        assertThat(response.courierId()).isEqualTo(newCourierId);
        assertThat(response.status()).isEqualTo(AssignmentStatus.ACTIVE);

        assertThat(currentAssignment.getStatus()).isEqualTo(AssignmentStatus.REASSIGNED);
        assertThat(currentAssignment.getReason()).isEqualTo("Courier vehicle breakdown");
        assertThat(oldWorkload.getActiveDeliveries()).isEqualTo(2);
        assertThat(newWorkload.getActiveDeliveries()).isEqualTo(2);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("parcel.reassigned");
    }

    @Test
    @DisplayName("Reassignment throws BadRequestException when reason is empty")
    void reassignFailsWithoutReason() {
        assertThatThrownBy(() -> assignmentService.reassign(parcelId, courierId, "  ", dispatcherId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mandatory");
    }

    @Test
    @DisplayName("Reassignment throws ConflictException when new courier is same as old courier")
    void reassignFailsForSameCourier() {
        Assignment currentAssignment = new Assignment(UUID.randomUUID(), parcelId, courierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, dispatcherId, Instant.now(), null);
        when(assignmentRepository.findByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(currentAssignment));

        assertThatThrownBy(() -> assignmentService.reassign(parcelId, courierId, "Breakdown", dispatcherId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be the same");
    }

    @Test
    @DisplayName("Auto-assign assigns eligible parcels and skips ones without eligible couriers")
    void autoAssignBatch() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UnassignedParcel parcel1 = new UnassignedParcel(p1, "SWD-1", null, "Colombo", "SMALL", 1.0, Instant.now().minusSeconds(500));
        UnassignedParcel parcel2 = new UnassignedParcel(p2, "SWD-2", null, "Kandy", "MEDIUM", 2.0, Instant.now().minusSeconds(100));

        when(parcelClient.getParcel(p1)).thenReturn(Optional.of(parcel1));
        when(parcelClient.getParcel(p2)).thenReturn(Optional.of(parcel2));

        AssignmentStrategy strategy = mock(AssignmentStrategy.class);
        when(strategyFactory.getStrategy(StrategyType.LEAST_LOADED)).thenReturn(strategy);

        CourierWorkload courier = new CourierWorkload(courierId, null, 1, 8, true, null);
        when(workloadRepository.findByAvailableTrue()).thenReturn(List.of(courier));
        when(workloadRepository.findByIdForUpdate(courierId)).thenReturn(Optional.of(courier));
        when(workloadRepository.save(any())).thenReturn(courier);

        // Parcel 1 gets a courier, Parcel 2 gets no eligible courier
        when(strategy.selectCourier(parcel1, List.of(courier))).thenReturn(Optional.of(courier));
        when(strategy.selectCourier(parcel2, List.of(courier))).thenReturn(Optional.empty());

        AutoAssignResponse response = assignmentService.autoAssign(List.of(p1, p2), StrategyType.LEAST_LOADED, dispatcherId);

        assertThat(response.assigned()).hasSize(1);
        assertThat(response.assigned().get(0).parcelId()).isEqualTo(p1);
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().get(0).parcelId()).isEqualTo(p2);
    }
}
