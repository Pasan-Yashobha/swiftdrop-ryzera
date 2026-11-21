package lk.swiftdrop.courier.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lk.swiftdrop.courier.client.ParcelClient;
import lk.swiftdrop.courier.client.ParcelStatusUpdateResponse;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ConflictException;
import lk.swiftdrop.courier.common.ErrorCode;
import lk.swiftdrop.courier.domain.AssignmentEventCursor;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.CourierAssignment;
import lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest;
import lk.swiftdrop.courier.repository.AssignmentEventCursorRepository;
import lk.swiftdrop.courier.repository.CourierAssignmentRepository;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import lk.swiftdrop.courier.repository.DeliveryProofRepository;
import lk.swiftdrop.courier.repository.ProcessedEventRepository;
import lk.swiftdrop.courier.storage.ProofImages;
import lk.swiftdrop.courier.storage.ProofStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryProofValidationTest {
    @Mock CourierAssignmentRepository assignments;
    @Mock CourierProfileRepository profiles;
    @Mock DeliveryProofRepository proofs;
    @Mock ProcessedEventRepository processed;
    @Mock AssignmentEventCursorRepository cursors;
    @Mock ParcelClient parcel;
    @Mock ProofStorageService storage;
    private DeliveryService service;
    private final UUID parcelId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private CourierAssignment assignment;

    @BeforeEach
    void setup() {
        service = new DeliveryService(assignments, profiles, proofs, processed, parcel, storage,
                Clock.fixed(now, ZoneOffset.UTC), cursors);
        assignment = new CourierAssignment(UUID.randomUUID(), parcelId, courierId, "SWD-1",
                AssignmentStatus.OUT_FOR_DELIVERY, now.minusSeconds(60), null, null);
        lenient().when(assignments.findByParcelId(parcelId)).thenReturn(List.of(assignment));
        lenient().when(cursors.lockParcel(parcelId)).thenReturn(Optional.of(new AssignmentEventCursor()));
    }

    private String key() { return "proofs/" + parcelId + "/11111111-1111-1111-1111-111111111111.png"; }
    private DeliveryStatusUpdateRequest delivered(String key) {
        return new DeliveryStatusUpdateRequest("DELIVERED", "Recipient", null, null, key);
    }

    static Stream<DeliveryStatusUpdateRequest> invalidFields() {
        return Stream.of(new DeliveryStatusUpdateRequest("DELIVERED", "x".repeat(101), null, null, null),
                new DeliveryStatusUpdateRequest("DELIVERED", "Name", null, null, "x".repeat(256)),
                new DeliveryStatusUpdateRequest("PICKED_UP", null, null, "x".repeat(11), null),
                new DeliveryStatusUpdateRequest("FAILED", null, "x".repeat(501), null, null));
    }

    @ParameterizedTest @MethodSource("invalidFields")
    void scalarLimitsProtectDirectServiceCalls(DeliveryStatusUpdateRequest request) {
        assertThatThrownBy(() -> service.updateDeliveryStatus(courierId, parcelId, request, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcel, proofs, storage);
    }

    @Test
    void foreignMissingAndTraversalKeysAreRejected() {
        for (String invalid : List.of("proofs/" + UUID.randomUUID() + "/11111111-1111-1111-1111-111111111111.png",
                "proofs/" + parcelId + "/../11111111-1111-1111-1111-111111111111.png",
                "data:image/png;base64,AAAA", key())) {
            assertThatThrownBy(() -> service.updateDeliveryStatus(courierId, parcelId, delivered(invalid), null))
                    .isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(parcel, proofs);
    }

    @Test
    void exactRecipientAndOtpLimitsAreAcceptedWithoutInventedOtpVerification() {
        when(storage.exists(key())).thenReturn(true);
        when(parcel.updateParcelStatus(eq(parcelId), any())).thenReturn(
                new ParcelStatusUpdateResponse(parcelId, null, null, "DELIVERED", null));
        var response = service.updateDeliveryStatus(courierId, parcelId,
                new DeliveryStatusUpdateRequest("DELIVERED", "N".repeat(100), null, "abcdefghij", key()), null);
        assertThat(response.status()).isEqualTo("DELIVERED");
        verify(parcel).updateParcelStatus(eq(parcelId), argThat(request -> request.otp().equals("abcdefghij")
                && request.recipientName().length() == 100 && request.photoObjectKey().equals(key())));
        verify(proofs).save(any());
    }

    static Stream<MockMultipartFile> invalidUploads() {
        return Stream.of(new MockMultipartFile("proofPhoto", "empty.jpg", "image/jpeg", new byte[0]),
                new MockMultipartFile("proofPhoto", "bad.jpg", "image/jpeg", "invalid".getBytes()),
                new MockMultipartFile("proofPhoto", "bad.jpg", "application/pdf", ProofImages.jpeg()),
                new MockMultipartFile("proofPhoto", "huge.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]));
    }

    @ParameterizedTest @MethodSource("invalidUploads")
    void uploadsAreRejectedBeforeStorageOrParcelMutation(MockMultipartFile file) {
        assertThatThrownBy(() -> service.updateDeliveryStatus(courierId, parcelId, delivered(null), file))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcel, storage, proofs);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void uploadAndExistingKeyCannotSilentlyOverrideEachOther() {
        var file = new MockMultipartFile("proofPhoto", "valid.png", "image/png", ProofImages.png());
        assertThatThrownBy(() -> service.updateDeliveryStatus(courierId, parcelId, delivered(key()), file))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage, parcel);
    }

    static Stream<RuntimeException> parcelFailures() {
        return Stream.of(new ConflictException("M4 invalid transition"),
                new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "M4 unavailable"));
    }

    @ParameterizedTest @MethodSource("parcelFailures")
    void parcelRejectOrOutageCannotProduceLocalDeliverySuccess(RuntimeException failure) {
        when(storage.exists(key())).thenReturn(true);
        when(parcel.updateParcelStatus(eq(parcelId), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.updateDeliveryStatus(courierId, parcelId, delivered(key()), null))
                .isSameAs(failure);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        assertThat(assignment.getCompletedAt()).isNull();
        verify(assignments, never()).save(any());
        verifyNoInteractions(proofs, profiles);
    }
    @Test
    void nulRecipientIsRejectedBeforeAnyRemoteMutation() {
        assertThatThrownBy(() -> service.updateDeliveryStatus(courierId, parcelId,
                new DeliveryStatusUpdateRequest("DELIVERED", "Name" + (char) 0, null, null, key()), null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcel, proofs, storage);
    }

}
