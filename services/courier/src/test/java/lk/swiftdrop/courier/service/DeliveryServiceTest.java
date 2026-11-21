package lk.swiftdrop.courier.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.client.ParcelClient;
import lk.swiftdrop.courier.client.ParcelStatusUpdateRequest;
import lk.swiftdrop.courier.client.ParcelStatusUpdateResponse;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ConflictException;
import lk.swiftdrop.courier.common.ErrorCode;
import lk.swiftdrop.courier.common.ForbiddenException;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.common.UnauthorizedException;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.CourierAssignment;
import lk.swiftdrop.courier.domain.CourierProfile;
import lk.swiftdrop.courier.domain.DeliveryProof;
import lk.swiftdrop.courier.domain.VehicleType;
import lk.swiftdrop.courier.dto.AssignedDeliveryResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest;
import lk.swiftdrop.courier.repository.CourierAssignmentRepository;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import lk.swiftdrop.courier.repository.DeliveryProofRepository;
import lk.swiftdrop.courier.repository.ProcessedEventRepository;
import lk.swiftdrop.courier.storage.ProofStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private CourierAssignmentRepository assignmentRepository;

    @Mock
    private CourierProfileRepository profileRepository;

    @Mock
    private DeliveryProofRepository deliveryProofRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ParcelClient parcelClient;

    @Mock
    private ProofStorageService proofStorageService;

    @Mock
    private lk.swiftdrop.courier.repository.AssignmentEventCursorRepository eventCursorRepository;

    private Clock clock;
    private DeliveryService deliveryService;

    private final Instant fixedNow = Instant.parse("2026-11-15T10:30:00Z"); // 16:00 Colombo time
    private final UUID courierId = UUID.fromString("c0000001-0000-0000-0000-000000000001");
    private final UUID otherCourierId = UUID.fromString("c0000002-0000-0000-0000-000000000002");
    private final UUID parcelId = UUID.fromString("b0000001-0000-0000-0000-000000000001");

    // Colombo midnight for 2026-11-15 is 2026-11-14T18:30:00Z
    private final Instant colomboStartOfDay = Instant.parse("2026-11-14T18:30:00Z");
    // Colombo start of next day (2026-11-16) is 2026-11-15T18:30:00Z
    private final Instant colomboStartOfNextDay = Instant.parse("2026-11-15T18:30:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneId.of("Asia/Colombo"));
        org.mockito.Mockito.lenient().when(proofStorageService.exists(anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(eventCursorRepository.lockParcel(parcelId))
                .thenReturn(Optional.of(new lk.swiftdrop.courier.domain.AssignmentEventCursor()));
        deliveryService = new DeliveryService(
                assignmentRepository,
                profileRepository,
                deliveryProofRepository,
                processedEventRepository,
                parcelClient,
                proofStorageService,
                clock, eventCursorRepository
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Today's Deliveries Date Filtering Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTodaysDeliveries queries repository using Asia/Colombo start of day and start of next day")
    void getTodaysDeliveries_queriesWithColomboBoundaries() {
        CourierAssignment a1 = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.ASSIGNED, fixedNow, null, null);

        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of(a1));

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trackingNo()).isEqualTo("SWD-1001");
    }

    @Test
    @DisplayName("getTodaysDeliveries excludes assignment immediately before start of day")
    void getTodaysDeliveries_excludesAssignmentImmediatelyBeforeStartOfDay() {
        Instant beforeStart = colomboStartOfDay.minusMillis(1);
        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of());

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).isEmpty();
        verify(assignmentRepository).findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay);
    }

    @Test
    @DisplayName("getTodaysDeliveries includes assignment exactly at start of day")
    void getTodaysDeliveries_includesAssignmentExactlyAtStartOfDay() {
        CourierAssignment atStart = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-START",
                AssignmentStatus.ASSIGNED, colomboStartOfDay, null, null);

        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of(atStart));

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trackingNo()).isEqualTo("SWD-START");
    }

    @Test
    @DisplayName("getTodaysDeliveries includes assignment immediately before start of next day")
    void getTodaysDeliveries_includesAssignmentImmediatelyBeforeStartOfNextDay() {
        Instant beforeNextDay = colomboStartOfNextDay.minusMillis(1);
        CourierAssignment beforeEnd = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-END",
                AssignmentStatus.ASSIGNED, beforeNextDay, null, null);

        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of(beforeEnd));

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).trackingNo()).isEqualTo("SWD-END");
    }

    @Test
    @DisplayName("getTodaysDeliveries excludes assignment exactly at start of next day")
    void getTodaysDeliveries_excludesAssignmentExactlyAtStartOfNextDay() {
        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of());

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getTodaysDeliveries preserves descending assignedAt ordering")
    void getTodaysDeliveries_preservesDescendingAssignedAtOrdering() {
        CourierAssignment newer = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-NEWER",
                AssignmentStatus.ASSIGNED, fixedNow.plusSeconds(3600), null, null);
        CourierAssignment older = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-OLDER",
                AssignmentStatus.ASSIGNED, fixedNow, null, null);

        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of(newer, older));

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).trackingNo()).isEqualTo("SWD-NEWER");
        assertThat(result.get(1).trackingNo()).isEqualTo("SWD-OLDER");
    }

    @Test
    @DisplayName("getTodaysDeliveries returns empty list when no assignments found")
    void getTodaysDeliveries_emptyListWhenNoAssignments() {
        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of());

        List<AssignedDeliveryResponse> result = deliveryService.getTodaysDeliveries(courierId);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getTodaysDeliveries handles UTC vs local date difference around midnight")
    void getTodaysDeliveries_utcLocalDifferenceAroundMidnight() {
        // At 2026-11-14T20:00:00Z, UTC date is Nov 14, but Colombo is 2026-11-15 01:30 AM (Nov 15)
        Instant lateNightUtc = Instant.parse("2026-11-14T20:00:00Z");
        Clock midnightClock = Clock.fixed(lateNightUtc, ZoneId.of("Asia/Colombo"));
        DeliveryService lateNightService = new DeliveryService(
                assignmentRepository,
                profileRepository,
                deliveryProofRepository,
                processedEventRepository,
                parcelClient,
                proofStorageService,
                midnightClock, eventCursorRepository
        );

        when(assignmentRepository.findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay))
                .thenReturn(List.of());

        lateNightService.getTodaysDeliveries(courierId);

        // Confirms it queries for Nov 15 Colombo day (2026-11-14T18:30:00Z to 2026-11-15T18:30:00Z)
        verify(assignmentRepository).findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                courierId, colomboStartOfDay, colomboStartOfNextDay);
    }

    // ─────────────────────────────────────────────────────────────
    // Status Transition & Delivery Proof Tests
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDeliveryStatus: successful intermediate status update (e.g. IN_TRANSIT)")
    void updateDeliveryStatus_intermediateStatus_success() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.PICKED_UP, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        ParcelStatusUpdateResponse m4Response = new ParcelStatusUpdateResponse(
                parcelId, "SWD-1001", "PICKED_UP", "IN_TRANSIT", fixedNow);
        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenReturn(m4Response);

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("IN_TRANSIT", null, null, null, null);
        DeliveryStatusResponse response = deliveryService.updateDeliveryStatus(courierId, parcelId, request, null);

        assertThat(response.status()).isEqualTo("IN_TRANSIT");
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.IN_TRANSIT);
        verify(assignmentRepository).save(assignment);
        verify(profileRepository, never()).save(any(CourierProfile.class));
    }

    @Test
    @DisplayName("updateDeliveryStatus: DELIVERED with proof photo upload updates local projection and decrements active deliveries")
    void updateDeliveryStatus_deliveredWithProof_success() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        CourierProfile profile = new CourierProfile(courierId, "Test Courier", "+94771234567",
                VehicleType.BIKE, "WP-1234", true, 3, 8, null, fixedNow);
        when(profileRepository.findById(courierId)).thenReturn(Optional.of(profile));

        MockMultipartFile photo = new MockMultipartFile("proofPhoto", "door.jpg", "image/jpeg", lk.swiftdrop.courier.storage.ProofImages.jpeg());
        when(proofStorageService.storeProofPhoto(eq(parcelId), eq("door.jpg"), eq("image/jpeg"), any(), eq((long) lk.swiftdrop.courier.storage.ProofImages.jpeg().length)))
                .thenReturn("proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");

        ParcelStatusUpdateResponse m4Response = new ParcelStatusUpdateResponse(
                parcelId, "SWD-1001", "OUT_FOR_DELIVERY", "DELIVERED", fixedNow);
        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenReturn(m4Response);

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "John Doe", null, null, null);
        DeliveryStatusResponse response = deliveryService.updateDeliveryStatus(courierId, parcelId, request, photo);

        assertThat(response.status()).isEqualTo("DELIVERED");
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(assignment.getCompletedAt()).isEqualTo(fixedNow);
        assertThat(profile.getActiveDeliveries()).isEqualTo(2);

        verify(assignmentRepository).save(assignment);
        verify(profileRepository).save(profile);
        verify(deliveryProofRepository).save(any(DeliveryProof.class));
    }

    @Test
    @DisplayName("updateDeliveryStatus: FAILED with valid failure reason updates local projection")
    void updateDeliveryStatus_failed_success() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        CourierProfile profile = new CourierProfile(courierId, "Test Courier", "+94771234567",
                VehicleType.BIKE, "WP-1234", true, 1, 8, null, fixedNow);
        when(profileRepository.findById(courierId)).thenReturn(Optional.of(profile));

        ParcelStatusUpdateResponse m4Response = new ParcelStatusUpdateResponse(
                parcelId, "SWD-1001", "OUT_FOR_DELIVERY", "FAILED", fixedNow);
        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenReturn(m4Response);

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("FAILED", null, "NOT_HOME", null, null);
        DeliveryStatusResponse response = deliveryService.updateDeliveryStatus(courierId, parcelId, request, null);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(assignment.getFailureReason()).isEqualTo("NOT_HOME");
        assertThat(profile.getActiveDeliveries()).isEqualTo(0);

        verify(assignmentRepository).save(assignment);
        verify(profileRepository).save(profile);
        verify(deliveryProofRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDeliveryStatus: rejects invalid status string")
    void updateDeliveryStatus_rejectsInvalidStatus() {
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("UNKNOWN_STATUS", null, null, null, null);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid or unsupported delivery status");
    }

    @Test
    @DisplayName("updateDeliveryStatus: DELIVERED rejects missing recipientName")
    void updateDeliveryStatus_delivered_rejectsMissingRecipient() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "", null, null, "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("recipientName is required when status is DELIVERED");
    }

    @Test
    @DisplayName("updateDeliveryStatus: DELIVERED rejects missing proof photo")
    void updateDeliveryStatus_delivered_rejectsMissingProofPhoto() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "John Doe", null, null, null);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Proof photo is required when status is DELIVERED");
    }

    @Test
    @DisplayName("updateDeliveryStatus: FAILED rejects missing failureReason")
    void updateDeliveryStatus_failed_rejectsMissingReason() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("FAILED", null, "", null, null);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("failureReason is required when status is FAILED");
    }

    @Test
    @DisplayName("updateDeliveryStatus: FAILED rejects invalid failureReason code")
    void updateDeliveryStatus_failed_rejectsInvalidReasonCode() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("FAILED", null, "LOST_ON_ROAD", null, null);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid failure reason 'LOST_ON_ROAD'");
    }

    @Test
    @DisplayName("updateDeliveryStatus: unassigned parcel returns 404 NOT_FOUND")
    void updateDeliveryStatus_unassignedParcel_returnsNotFound() {
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of());

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("IN_TRANSIT", null, null, null, null);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Delivery assignment not found for parcel");
    }

    @Test
    @DisplayName("updateDeliveryStatus: parcel assigned to different courier returns 403 FORBIDDEN")
    void updateDeliveryStatus_assignedToOtherCourier_returnsForbidden() {
        CourierAssignment otherAssignment = new CourierAssignment(UUID.randomUUID(), parcelId, otherCourierId, "SWD-1001",
                AssignmentStatus.ASSIGNED, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(otherAssignment));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("IN_TRANSIT", null, null, null, null);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("assigned to another courier");
    }

    @Test
    @DisplayName("updateDeliveryStatus: duplicate retry of terminal status DELIVERED is idempotent")
    void updateDeliveryStatus_idempotentRetryOnTerminalStatus() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.DELIVERED, fixedNow.minusSeconds(600), fixedNow.minusSeconds(600), null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "John Doe", null, null, "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");
        DeliveryStatusResponse response = deliveryService.updateDeliveryStatus(courierId, parcelId, request, null);

        assertThat(response.status()).isEqualTo("DELIVERED");
        verify(parcelClient, never()).updateParcelStatus(any(), any());
        verify(profileRepository, never()).save(any(CourierProfile.class));
    }

    @Test
    @DisplayName("updateDeliveryStatus: when M4 fails (e.g. 409 Conflict), local assignment is NOT modified")
    void updateDeliveryStatus_whenM4Fails_localStateUntouched() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenThrow(new ConflictException("Invalid parcel state transition in Parcel Service"));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "John Doe", null, null, "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(ConflictException.class);

        // Crucial safety check: local assignment must NOT be marked DELIVERED
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        assertThat(assignment.getCompletedAt()).isNull();
        verify(assignmentRepository, never()).save(assignment);
        verify(deliveryProofRepository, never()).save(any());
        verify(profileRepository, never()).save(any(CourierProfile.class));
    }

    @Test
    @DisplayName("updateDeliveryStatus: when M4 is offline (503 Service Unavailable), exception bubbles up and local state untouched")
    void updateDeliveryStatus_whenM4Offline_localStateUntouched() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));

        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Parcel Service is temporarily unavailable"));

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "John Doe", null, null, "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).code() == ErrorCode.SERVICE_UNAVAILABLE);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        verify(assignmentRepository, never()).save(assignment);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ErrorCode.class, names = {
            "COURIER_MISMATCH", "IDEMPOTENCY_KEY_IN_USE", "OPTIMISTIC_LOCK", "IDEMPOTENCY_KEY_REUSED", "PARCEL_REJECTED", "INVALID_STATE", "SERVICE_UNAVAILABLE"})
    void parcelRejectionsNeverMutateAssignmentProofOrWorkload(ErrorCode code) {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1001",
                AssignmentStatus.OUT_FOR_DELIVERY, fixedNow, null, null);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));
        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenThrow(new ApiException(code, "M4 rejected request"));
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest("DELIVERED", "Recipient", null, null,
                "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(ApiException.class).satisfies(e -> {
                    assertThat(((ApiException) e).code()).isEqualTo(code);
                    int expectedStatus = switch (code) {
                        case IDEMPOTENCY_KEY_REUSED, PARCEL_REJECTED -> 422;
                        case SERVICE_UNAVAILABLE -> 503;
                        default -> 409;
                    };
                    assertThat(((ApiException) e).status().value()).isEqualTo(expectedStatus);
                });
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        assertThat(assignment.getCompletedAt()).isNull();
        verify(assignmentRepository, never()).save(any());
        verify(deliveryProofRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
    }
    @Test
    @DisplayName("M4 204 acknowledgement uses existing tracking/status and Courier clock")
    void updateDeliveryStatus_noContentAcknowledgement() {
        CourierAssignment assignment = CourierAssignment.createNew(
                parcelId, courierId, "SWD-EXISTING", fixedNow.minusSeconds(60));
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));
        when(parcelClient.updateParcelStatus(eq(parcelId), any(ParcelStatusUpdateRequest.class)))
                .thenReturn(new ParcelStatusUpdateResponse(parcelId, null, null, "PICKED_UP", null));

        var response = deliveryService.updateDeliveryStatus(courierId, parcelId,
                new DeliveryStatusUpdateRequest("PICKED_UP", null, null, null, null), null);

        assertThat(response.trackingNo()).isEqualTo("SWD-EXISTING");
        assertThat(response.previousStatus()).isEqualTo("ASSIGNED");
        assertThat(response.status()).isEqualTo("PICKED_UP");
        assertThat(response.updatedAt()).isEqualTo(fixedNow);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
        verify(assignmentRepository).save(assignment);
        verify(deliveryProofRepository, never()).save(any());
    }

    @Test
    void acknowledgedFailedRetryRestoresLocalWorkloadOnceAndClearsClosureTime() {
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "TEST-RETRY",
                AssignmentStatus.FAILED, fixedNow.minusSeconds(60), fixedNow.minusSeconds(10), "NOT_HOME");
        CourierProfile profile = new CourierProfile(courierId, "Test courier", "", VehicleType.BIKE, "", true, 2, 8, null, fixedNow);
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));
        when(profileRepository.findById(courierId)).thenReturn(Optional.of(profile));
        when(parcelClient.updateParcelStatus(eq(parcelId), any())).thenAnswer(call -> {
            ParcelStatusUpdateRequest req = call.getArgument(1);
            return new ParcelStatusUpdateResponse(parcelId, null, null, req.status(), null);
        });
        UUID assignmentId = assignment.getId();
        var retry = new DeliveryStatusUpdateRequest("OUT_FOR_DELIVERY", null, null, null, null);
        deliveryService.updateDeliveryStatus(courierId, parcelId, retry, null);
        assertThat(profile.getActiveDeliveries()).isEqualTo(3);
        assertThat(assignment.getCompletedAt()).isNull();
        assertThat(assignment.getId()).isEqualTo(assignmentId);
        assertThat(assignment.getAssignedAt()).isEqualTo(fixedNow.minusSeconds(60));
        assertThat(assignment.getFailureReason()).isEqualTo("NOT_HOME");
        deliveryService.updateDeliveryStatus(courierId, parcelId, retry, null);
        assertThat(profile.getActiveDeliveries()).isEqualTo(3);
        var delivered = new DeliveryStatusUpdateRequest("DELIVERED", "Test recipient", null, null,
                "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.jpg");
        deliveryService.updateDeliveryStatus(courierId, parcelId, delivered, null);
        deliveryService.updateDeliveryStatus(courierId, parcelId, delivered, null);
        assertThat(profile.getActiveDeliveries()).isEqualTo(2);
        assertThat(assignment.getCompletedAt()).isEqualTo(fixedNow);
        verify(profileRepository, org.mockito.Mockito.times(2)).save(profile);
        verify(deliveryProofRepository).save(any());
        verify(parcelClient, org.mockito.Mockito.times(3)).updateParcelStatus(eq(parcelId), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ErrorCode.class, names = {"INVALID_STATE", "COURIER_MISMATCH", "SERVICE_UNAVAILABLE", "IDEMPOTENCY_KEY_REUSED", "PARCEL_REJECTED"})
    void rejectedOrUncertainRetryPreservesFailedAssignmentAndWorkload(ErrorCode code) {
        Instant failedAt = fixedNow.minusSeconds(10);
        CourierAssignment assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "TEST-RETRY",
                AssignmentStatus.FAILED, fixedNow.minusSeconds(60), failedAt, "NOT_HOME");
        when(assignmentRepository.findByParcelId(parcelId)).thenReturn(List.of(assignment));
        when(parcelClient.updateParcelStatus(eq(parcelId), any())).thenThrow(new ApiException(code, "not confirmed"));
        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(courierId, parcelId,
                new DeliveryStatusUpdateRequest("OUT_FOR_DELIVERY", null, null, null, null), null)).isInstanceOf(ApiException.class);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(assignment.getCompletedAt()).isEqualTo(failedAt);
        assertThat(assignment.getFailureReason()).isEqualTo("NOT_HOME");
        verify(assignmentRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(deliveryProofRepository, never()).save(any());
    }

}
