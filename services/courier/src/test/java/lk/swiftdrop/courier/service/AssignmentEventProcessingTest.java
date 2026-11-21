package lk.swiftdrop.courier.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lk.swiftdrop.courier.client.ParcelClient;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.CourierAssignment;
import lk.swiftdrop.courier.domain.CourierProfile;
import lk.swiftdrop.courier.domain.VehicleType;
import lk.swiftdrop.courier.repository.AssignmentEventCursorRepository;
import lk.swiftdrop.courier.repository.CourierAssignmentRepository;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import lk.swiftdrop.courier.repository.ProcessedEventRepository;
import lk.swiftdrop.courier.storage.ProofStorageService;
import org.h2.api.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DeliveryService.class, AssignmentEventProcessingTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssignmentEventProcessingTest {
    private static final Instant TIME = Instant.parse("2026-11-15T10:00:00Z");
    @Autowired DeliveryService service;
    @Autowired CourierAssignmentRepository assignments;
    @Autowired CourierProfileRepository profiles;
    @Autowired ProcessedEventRepository processed;
    @Autowired AssignmentEventCursorRepository cursors;
    @Autowired JdbcTemplate jdbc;
    @MockBean ParcelClient parcelClient;
    @MockBean ProofStorageService proofStorage;
    UUID parcel;
    UUID oldCourier;
    UUID newCourier;

    @Test
    void missingOptionalTrackingMetadataDoesNotLoseOrResynchronizeM6Assignment() {
        org.mockito.Mockito.when(parcelClient.getTrackingNumber(parcel)).thenReturn(java.util.Optional.empty());
        String eventId = "metadata-unavailable-" + parcel;
        service.handleAssignment(eventId, parcel, oldCourier, null, TIME);
        service.handleAssignment(eventId, parcel, oldCourier, null, TIME);

        var history = assignments.findByParcelId(parcel);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getCourierId()).isEqualTo(oldCourier);
        assertThat(history.get(0).getTrackingNo()).isNull();
        assertThat(profiles.findById(oldCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
        assertThat(processed.existsById(eventId)).isTrue();
        org.mockito.Mockito.verify(parcelClient).getTrackingNumber(parcel);
        // M7 projects Dispatch's event; it does not manufacture a Parcel assignment PATCH.
        org.mockito.Mockito.verifyNoMoreInteractions(parcelClient);
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean Clock clock() { return Clock.fixed(TIME.plusSeconds(100), ZoneOffset.UTC); }
    }

    @BeforeEach
    void fixtures() {
        parcel = UUID.randomUUID(); oldCourier = UUID.randomUUID(); newCourier = UUID.randomUUID();
        profiles.save(CourierProfile.createNew(oldCourier, "Old", "phone", VehicleType.BIKE, "A", 8, null, TIME));
        profiles.save(CourierProfile.createNew(newCourier, "New", "phone", VehicleType.BIKE, "B", 8, null, TIME));
    }

    @Test
    void sameEventIdAndDifferentIdsForSameAssignmentAreIdempotent() {
        var first = service.handleAssignment("assign-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        service.handleAssignment("assign-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        service.handleAssignment("logical-" + parcel, parcel, oldCourier, "SWD-1", TIME.plusSeconds(1));
        assertThat(assignments.findByParcelId(parcel)).hasSize(1);
        assertThat(profiles.findById(oldCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
        assertThat(assignments.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void reassignmentKeepsHistoryAndLogicalDuplicateDoesNotIncrementAgain() {
        service.handleAssignment("assign-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        service.handleReassignment("move-" + parcel, parcel, oldCourier, newCourier, "Unavailable", TIME.plusSeconds(2));
        service.handleReassignment("move-" + parcel, parcel, oldCourier, newCourier, "Unavailable", TIME.plusSeconds(2));
        service.handleReassignment("move-copy-" + parcel, parcel, oldCourier, newCourier, "Unavailable", TIME.plusSeconds(3));
        assertThat(assignments.findByParcelId(parcel)).hasSize(2);
        assertThat(assignments.findByParcelId(parcel).stream().filter(a -> a.getStatus() == AssignmentStatus.REASSIGNED)).hasSize(1);
        assertThat(profiles.findById(oldCourier).orElseThrow().getActiveDeliveries()).isZero();
        assertThat(profiles.findById(newCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
    }

    @Test
    void olderAssignmentAfterReassignmentIsIgnored() {
        service.handleAssignment("assign-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        service.handleReassignment("move-" + parcel, parcel, oldCourier, newCourier, "Unavailable", TIME.plusSeconds(2));
        service.handleAssignment("late-" + parcel, parcel, oldCourier, "SWD-1", TIME.plusSeconds(1));
        service.handleReassignment("late-move-" + parcel, parcel, newCourier, oldCourier, "Late", TIME.plusSeconds(1));
        assertThat(assignments.findByParcelId(parcel)).hasSize(2);
        assertThat(profiles.findById(newCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
        assertThat(processed.existsById("late-" + parcel)).isTrue();
    }

    @Test
    void equalTimestampConflictIsQuarantinedWithoutChoosingAnOrder() {
        service.handleAssignment("assign-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        assertThatThrownBy(() -> service.handleReassignment("tie-" + parcel, parcel,
                oldCourier, newCourier, "Unavailable", TIME))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(assignments.findByParcelId(parcel)).hasSize(1);
        assertThat(processed.existsById("tie-" + parcel)).isFalse();
    }

    @Test
    void missingHistoryAndConflictingOwnersAreQuarantined() {
        assertThatThrownBy(() -> service.handleReassignment("missing-" + parcel, parcel,
                oldCourier, newCourier, "Unavailable", TIME))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        service.handleAssignment("assign-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        assertThatThrownBy(() -> service.handleAssignment("conflict-" + parcel, parcel, newCourier,
                "SWD-1", TIME.plusSeconds(1))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(assignments.findByParcelId(parcel)).hasSize(1);
    }

    @Test
    void preExistingHistoryBootstrapsWithoutReopeningCompletedAssignment() {
        var historical = new CourierAssignment(UUID.randomUUID(), parcel, oldCourier, "SWD-1",
                AssignmentStatus.DELIVERED, TIME, TIME.plusSeconds(1), null);
        assignments.save(historical);
        service.handleAssignment("duplicate-" + parcel, parcel, oldCourier, "SWD-1", TIME.plusSeconds(2));
        assertThat(assignments.findByParcelId(parcel)).hasSize(1);
        assertThat(assignments.findById(historical.getId()).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(profiles.findById(oldCourier).orElseThrow().getActiveDeliveries()).isZero();
    }

    @Test
    void actualDatabaseInsertFailureRollsBackEventMarkerAndCursorThenCanBeRetried() {
        FailOnceTrigger.fail.set(true);
        jdbc.execute("CREATE TRIGGER stage44_fail_once BEFORE INSERT ON courier_assignments FOR EACH ROW CALL '"
                + FailOnceTrigger.class.getName() + "'");
        try {
            assertThatThrownBy(() -> service.handleAssignment("db-" + parcel, parcel, oldCourier, "SWD-1", TIME))
                    .isInstanceOf(RuntimeException.class);
            assertThat(processed.existsById("db-" + parcel)).isFalse();
            assertThat(cursors.existsById(parcel)).isFalse();
            assertThat(assignments.findByParcelId(parcel)).isEmpty();
            service.handleAssignment("db-" + parcel, parcel, oldCourier, "SWD-1", TIME);
            assertThat(processed.existsById("db-" + parcel)).isTrue();
            assertThat(assignments.findByParcelId(parcel)).hasSize(1);
            assertThat(profiles.findById(oldCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER stage44_fail_once");
            FailOnceTrigger.fail.set(false);
        }
    }

    @Test
    void simultaneousLogicalDuplicatesCreateOneAssignment() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return service.handleAssignment("parallel-a-" + parcel, parcel, oldCourier, "SWD-1", TIME);
            });
            var second = executor.submit(() -> {
                start.await();
                return service.handleAssignment("parallel-b-" + parcel, parcel, oldCourier, "SWD-1", TIME);
            });
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).getId())
                    .isEqualTo(second.get(10, java.util.concurrent.TimeUnit.SECONDS).getId());
        }
        assertThat(assignments.findByParcelId(parcel)).hasSize(1);
        assertThat(profiles.findById(oldCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
    }

    @Test
    void unrelatedOlderOwnerDoesNotProveAReassignmentIsDuplicate() {
        UUID thirdCourier = UUID.randomUUID();
        profiles.save(CourierProfile.createNew(thirdCourier, "Third", "phone", VehicleType.BIKE, "C", 8, null, TIME));
        service.handleAssignment("start-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        service.handleReassignment("ab-" + parcel, parcel, oldCourier, newCourier, "Move", TIME.plusSeconds(2));
        service.handleReassignment("bc-" + parcel, parcel, newCourier, thirdCourier, "Move", TIME.plusSeconds(4));
        service.handleReassignment("cb-" + parcel, parcel, thirdCourier, newCourier, "Move", TIME.plusSeconds(6));
        assertThatThrownBy(() -> service.handleReassignment("ambiguous-ab-" + parcel, parcel,
                oldCourier, newCourier, "Move", TIME.plusSeconds(7)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(assignments.findByParcelId(parcel)).hasSize(4);
        assertThat(processed.existsById("ambiguous-ab-" + parcel)).isFalse();
        assertThat(profiles.findById(newCourier).orElseThrow().getActiveDeliveries()).isEqualTo(1);
    }

    @Test
    void currentCourierCanUpdateAfterReassignmentButPreviousCourierCannot() {
        service.handleAssignment("owned-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        service.handleReassignment("owned-move-" + parcel, parcel, oldCourier, newCourier, "Move", TIME.plusSeconds(2));
        var request = new lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest("PICKED_UP", null, null, null, null);
        assertThatThrownBy(() -> service.updateDeliveryStatus(oldCourier, parcel, request, null))
                .isInstanceOf(lk.swiftdrop.courier.common.ForbiddenException.class);
        org.mockito.Mockito.verify(parcelClient, org.mockito.Mockito.never()).updateParcelStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(parcelClient.updateParcelStatus(org.mockito.ArgumentMatchers.eq(parcel),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                new lk.swiftdrop.courier.client.ParcelStatusUpdateResponse(parcel, null, null, "PICKED_UP", null));
        assertThat(service.updateDeliveryStatus(newCourier, parcel, request, null).status()).isEqualTo("PICKED_UP");
        assertThat(assignments.findByParcelId(parcel)).hasSize(2);
        assertThat(assignments.findByParcelId(parcel).stream()
                .filter(a -> a.getCourierId().equals(oldCourier)).findFirst().orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.REASSIGNED);
    }

    @Test
    void ambiguousOwnershipIsRejectedBeforeParcelCall() {
        assignments.save(CourierAssignment.createNew(parcel, oldCourier, "SWD-1", TIME));
        assignments.save(CourierAssignment.createNew(parcel, newCourier, "SWD-1", TIME.plusSeconds(1)));
        assertThatThrownBy(() -> service.updateDeliveryStatus(oldCourier, parcel,
                new lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest("PICKED_UP", null, null, null, null), null))
                .isInstanceOf(lk.swiftdrop.courier.common.ConflictException.class);
        org.mockito.Mockito.verify(parcelClient, org.mockito.Mockito.never()).updateParcelStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(assignments.findByParcelId(parcel)).hasSize(2);
    }

    @Test
    void parcelRejectionLeavesCommittedDeliveryStateUntouched() {
        service.handleAssignment("reject-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        org.mockito.Mockito.when(parcelClient.updateParcelStatus(org.mockito.ArgumentMatchers.eq(parcel),
                org.mockito.ArgumentMatchers.any())).thenThrow(
                new lk.swiftdrop.courier.common.ConflictException("M4 rejected invalid transition"));
        assertThatThrownBy(() -> service.updateDeliveryStatus(oldCourier, parcel,
                new lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest("IN_TRANSIT", null, null, null, null), null))
                .isInstanceOf(lk.swiftdrop.courier.common.ConflictException.class);
        assertThat(assignments.findByParcelId(parcel).get(0).getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void statusRejectsCursorThatPointsToADifferentAssignment() {
        service.handleAssignment("cursor-" + parcel, parcel, oldCourier, "SWD-1", TIME);
        var other = assignments.save(CourierAssignment.createNew(UUID.randomUUID(), oldCourier, "OTHER", TIME));
        var cursor = cursors.findById(parcel).orElseThrow();
        cursor.record(other.getId(), TIME);
        cursors.save(cursor);
        assertThatThrownBy(() -> service.updateDeliveryStatus(oldCourier, parcel,
                new lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest("PICKED_UP", null, null, null, null), null))
                .isInstanceOf(lk.swiftdrop.courier.common.ConflictException.class);
        org.mockito.Mockito.verify(parcelClient, org.mockito.Mockito.never()).updateParcelStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void statusRejectsHistoryWithNoCurrentOwner() {
        var closed = CourierAssignment.createNew(parcel, oldCourier, "SWD-1", TIME);
        closed.reassign("Moved", TIME.plusSeconds(2));
        assignments.save(closed);
        assertThatThrownBy(() -> service.updateDeliveryStatus(oldCourier, parcel,
                new lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest("PICKED_UP", null, null, null, null), null))
                .isInstanceOf(lk.swiftdrop.courier.common.ConflictException.class);
        assertThat(assignments.findByParcelId(parcel).get(0).getStatus()).isEqualTo(AssignmentStatus.REASSIGNED);
    }

    @Test
    void statusRejectsAnOwnerOlderThanUnresolvedReassignmentHistory() {
        var closed = CourierAssignment.createNew(parcel, newCourier, "SWD-1", TIME);
        closed.reassign("Moved", TIME.plusSeconds(3));
        assignments.save(closed);
        assignments.save(CourierAssignment.createNew(parcel, oldCourier, "SWD-1", TIME.plusSeconds(1)));
        assertThatThrownBy(() -> service.updateDeliveryStatus(oldCourier, parcel,
                new lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest("PICKED_UP", null, null, null, null), null))
                .isInstanceOf(lk.swiftdrop.courier.common.ConflictException.class);
    }

    public static class FailOnceTrigger implements Trigger {
        static final AtomicBoolean fail = new AtomicBoolean();
        @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            if (fail.getAndSet(false)) { throw new SQLException("Injected database failure", "08006"); }
        }
    }
}
