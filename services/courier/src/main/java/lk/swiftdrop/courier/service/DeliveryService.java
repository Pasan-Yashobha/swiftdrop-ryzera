package lk.swiftdrop.courier.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.client.ParcelClient;
import lk.swiftdrop.courier.client.ParcelStatusUpdateRequest;
import lk.swiftdrop.courier.client.ParcelStatusUpdateResponse;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ConflictException;
import lk.swiftdrop.courier.common.ForbiddenException;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.AssignmentEventCursor;
import lk.swiftdrop.courier.repository.AssignmentEventCursorRepository;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import lk.swiftdrop.courier.domain.CourierAssignment;
import lk.swiftdrop.courier.domain.DeliveryProof;
import lk.swiftdrop.courier.domain.FailureReasonCode;
import lk.swiftdrop.courier.dto.AssignedDeliveryResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusResponse;
import lk.swiftdrop.courier.dto.DeliveryStatusUpdateRequest;
import lk.swiftdrop.courier.repository.CourierAssignmentRepository;
import lk.swiftdrop.courier.repository.CourierProfileRepository;
import lk.swiftdrop.courier.repository.DeliveryProofRepository;
import lk.swiftdrop.courier.repository.ProcessedEventRepository;
import lk.swiftdrop.courier.storage.ProofStorageService;
import lk.swiftdrop.courier.storage.ProofImageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private final CourierAssignmentRepository assignmentRepository;
    private final CourierProfileRepository profileRepository;
    private final DeliveryProofRepository deliveryProofRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final AssignmentEventCursorRepository eventCursorRepository;
    private final ParcelClient parcelClient;
    private final ProofStorageService proofStorageService;
    private final Clock clock;

    public DeliveryService(CourierAssignmentRepository assignmentRepository,
                           CourierProfileRepository profileRepository,
                           DeliveryProofRepository deliveryProofRepository,
                           ProcessedEventRepository processedEventRepository,
                           ParcelClient parcelClient,
                           ProofStorageService proofStorageService,
                           Clock clock,
                           AssignmentEventCursorRepository eventCursorRepository) {
        this.assignmentRepository = assignmentRepository;
        this.profileRepository = profileRepository;
        this.deliveryProofRepository = deliveryProofRepository;
        this.processedEventRepository = processedEventRepository;
        this.parcelClient = parcelClient;
        this.proofStorageService = proofStorageService;
        this.clock = clock;
        this.eventCursorRepository = eventCursorRepository;
    }

    @Transactional(readOnly = true)
    public List<AssignedDeliveryResponse> getTodaysDeliveries(UUID courierId) {
        Objects.requireNonNull(courierId, "courierId must not be null");

        // Calculate start of today and start of next day using Asia/Colombo timezone
        ZonedDateTime nowInColombo = clock.instant().atZone(BUSINESS_ZONE);
        Instant startOfDay = nowInColombo.toLocalDate().atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant startOfNextDay = nowInColombo.toLocalDate().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

        List<CourierAssignment> assignments = assignmentRepository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
                        courierId, startOfDay, startOfNextDay);

        return assignments.stream()
                .map(AssignedDeliveryResponse::from)
                .toList();
    }

    /**
     * Submits a courier-driven parcel delivery status update.
     * Validates courier assignment ownership, status transitions, proof photo upload,
     * forwards to authoritative M4 Parcel Service, and synchronizes local assignment projection.
     */
    @Transactional
    public DeliveryStatusResponse updateDeliveryStatus(UUID courierId, UUID parcelId,
                                                       DeliveryStatusUpdateRequest request,
                                                       MultipartFile proofPhoto) {
        Objects.requireNonNull(courierId, "courierId must not be null");
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        request.validateFields();

        // 1. Validate requested status
        if (!AssignmentStatus.isValidCourierStatus(request.status())) {
            throw new BadRequestException("Invalid or unsupported delivery status: '" + request.status()
                    + "'. Allowed courier statuses: PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED");
        }
        AssignmentStatus targetStatus = AssignmentStatus.valueOf(request.status().trim().toUpperCase());

        // Serialize status authorization with Stage 4.4 assignment/reassignment consumption.
        CourierAssignment assignment = currentOwnedAssignment(courierId, parcelId);
        AssignmentStatus currentStatus = assignment.getStatus();

        validateProofInput(parcelId, targetStatus, request, proofPhoto);

        // 3. Idempotency / Duplicate terminal completion guard
        if (currentStatus == targetStatus && targetStatus.isTerminal()) {
            log.info("Parcel {} is already in terminal status {}. Returning existing assignment idempotently.",
                    parcelId, targetStatus);
            return new DeliveryStatusResponse(
                    parcelId,
                    assignment.getTrackingNo(),
                    currentStatus.name(),
                    targetStatus.name(),
                    assignment.getCompletedAt() != null ? assignment.getCompletedAt() : clock.instant()
            );
        }

        // 4. Status-specific validation & delivery proof handling
        String photoObjectKey = null;
        String recipientName = null;
        String failureReason = null;

        if (targetStatus == AssignmentStatus.DELIVERED) {
            // recipientName validation
            if (request.recipientName() == null || request.recipientName().isBlank()) {
                throw new BadRequestException("recipientName is required when status is DELIVERED");
            }
            recipientName = request.recipientName().trim();

            // proof photo handling
            if (proofPhoto != null && !proofPhoto.isEmpty()) {
                try (var input = proofPhoto.getInputStream()) {
                    photoObjectKey = proofStorageService.storeProofPhoto(
                            parcelId,
                            proofPhoto.getOriginalFilename(),
                            proofPhoto.getContentType(),
                            input,
                            proofPhoto.getSize()
                    );
                } catch (IOException e) {
                    throw new BadRequestException("Failed to read proof photo upload: " + e.getMessage());
                }
            } else if (request.photoObjectKey() != null && !request.photoObjectKey().isBlank()) {
                photoObjectKey = request.photoObjectKey().trim();
            } else {
                throw new BadRequestException("Proof photo is required when status is DELIVERED");
            }
        } else if (targetStatus == AssignmentStatus.FAILED) {
            // failureReason validation
            if (request.failureReason() == null || request.failureReason().isBlank()) {
                throw new BadRequestException("failureReason is required when status is FAILED");
            }
            if (!FailureReasonCode.isValid(request.failureReason())) {
                throw new BadRequestException("Invalid failure reason '" + request.failureReason()
                        + "'. Allowed codes: NOT_HOME, ADDRESS_NOT_FOUND, REFUSED, DAMAGED, ACCESS_RESTRICTED, WEATHER, OTHER");
            }
            failureReason = request.failureReason().trim().toUpperCase();
            // Do not send proof fields for failure
            photoObjectKey = null;
            recipientName = null;
        }

        // 5. Forward status update to authoritative M4 Parcel Service
        ParcelStatusUpdateRequest m4Request = new ParcelStatusUpdateRequest(
                targetStatus.name(),
                courierId,
                recipientName,
                photoObjectKey,
                request.otp(),
                failureReason
        );

        // M4 performs authoritative transition check and database update.
        // If M4 throws an exception (400, 401, 403, 404, 409, 503), the exception bubbles up,
        // transaction rolls back, and local assignment is NOT modified!
        ParcelStatusUpdateResponse m4Response = parcelClient.updateParcelStatus(parcelId, m4Request);

        // 6. Update local Courier Service assignment projection
        Instant now = clock.instant();
        assignment.updateStatus(targetStatus, now, failureReason);
        if (m4Response.trackingNo() != null && !m4Response.trackingNo().isBlank()) {
            assignment.updateTrackingNo(m4Response.trackingNo());
        }
        assignmentRepository.save(assignment);

        // 7. If DELIVERED, persist proof record in delivery_proofs
        if (targetStatus == AssignmentStatus.DELIVERED && photoObjectKey != null) {
            DeliveryProof proof = DeliveryProof.create(assignment.getId(), recipientName, photoObjectKey, now);
            deliveryProofRepository.save(proof);
        }

        // 8. Update courier active delivery count exactly once upon terminal completion
        if (targetStatus.isTerminal() && !currentStatus.isTerminal()) {
            profileRepository.findById(courierId).ifPresent(profile -> {
                profile.decrementActiveDeliveries(now);
                profileRepository.save(profile);
                log.info("Decremented active deliveries for courier {}. Current active: {}",
                        courierId, profile.getActiveDeliveries());
            });
        }

        // M4 permits retrying FAILED -> OUT_FOR_DELIVERY with the same assigned courier.
        // Restore only the local active projection, after acknowledgement, once per reopen.
        if (currentStatus == AssignmentStatus.FAILED && targetStatus == AssignmentStatus.OUT_FOR_DELIVERY) {
            profileRepository.findById(courierId).ifPresent(profile -> {
                profile.incrementActiveDeliveries(now);
                profileRepository.save(profile);
            });
        }

        log.info("Successfully updated parcel {} status from {} to {} for courier {}",
                parcelId, currentStatus, targetStatus, courierId);

        return new DeliveryStatusResponse(
                parcelId,
                assignment.getTrackingNo() != null ? assignment.getTrackingNo() : m4Response.trackingNo(),
                m4Response.previousStatus() != null ? m4Response.previousStatus() : currentStatus.name(),
                targetStatus.name(),
                m4Response.updatedAt() != null ? m4Response.updatedAt() : now
        );
    }

    @Transactional
    public CourierAssignment handleAssignment(String eventId, UUID parcelId, UUID courierId,
                                              String trackingNo, Instant assignedAt) {
        AssignmentEventCursor cursor = lockEvent(eventId, parcelId, assignedAt);
        CourierAssignment current = currentAssignment(cursor, parcelId);
        if (processedEventRepository.insertIfAbsent(eventId, "parcel.assigned", clock.instant()) == 0
                || isOlder(cursor, assignedAt)) {
            return current;
        }
        if (current != null) {
            if (!current.getCourierId().equals(courierId)) {
                throw ambiguous("Assignment conflicts with the recorded courier; reassignment is required");
            }
            // A different event ID for the same assignment must not reopen or increment it.
            cursor.record(current.getId(), assignedAt);
            return current;
        }
        String resolvedTracking = trackingNo;
        if (resolvedTracking == null || resolvedTracking.isBlank()) {
            resolvedTracking = parcelClient.getTrackingNumber(parcelId).orElse(null);
        }
        CourierAssignment saved = assignmentRepository.save(
                CourierAssignment.createNew(parcelId, courierId, resolvedTracking, assignedAt));
        profileRepository.findById(courierId).ifPresent(profile -> {
            profile.incrementActiveDeliveries(clock.instant());
            profileRepository.save(profile);
        });
        cursor.record(saved.getId(), assignedAt);
        return saved;
    }

    @Transactional
    public void handleReassignment(String eventId, UUID parcelId, UUID oldCourierId,
                                   UUID newCourierId, String reason, Instant reassignedAt) {
        AssignmentEventCursor cursor = lockEvent(eventId, parcelId, reassignedAt);
        CourierAssignment current = currentAssignment(cursor, parcelId);
        if (processedEventRepository.insertIfAbsent(eventId, "parcel.reassigned", clock.instant()) == 0
                || isOlder(cursor, reassignedAt)) {
            return;
        }
        if (current == null) {
            throw ambiguous("Cannot reconcile reassignment without assignment history");
        }
        if (current.getCourierId().equals(newCourierId)) {
            boolean previousRecorded = assignmentRepository.findByParcelId(parcelId).stream()
                    .anyMatch(a -> a.getCourierId().equals(oldCourierId)
                            && a.getStatus() == AssignmentStatus.REASSIGNED
                            && a.getCompletedAt() != null
                            && a.getCompletedAt().equals(current.getAssignedAt()));
            if (!previousRecorded) {
                throw ambiguous("Cannot verify duplicate reassignment history");
            }
            cursor.record(current.getId(), reassignedAt);
            return;
        }
        if (!current.getCourierId().equals(oldCourierId)) {
            throw ambiguous("Reassignment previous courier conflicts with local history");
        }
        if (reassignedAt.equals(cursor.getLastOccurredAt())) {
            throw ambiguous("Conflicting assignment events have equal timestamps");
        }
        String trackingNo = current.getTrackingNo();
        current.reassign(reason, reassignedAt);
        assignmentRepository.save(current);
        profileRepository.findById(oldCourierId).ifPresent(profile -> {
            profile.decrementActiveDeliveries(clock.instant());
            profileRepository.save(profile);
        });
        if (trackingNo == null || trackingNo.isBlank()) {
            trackingNo = parcelClient.getTrackingNumber(parcelId).orElse(null);
        }
        CourierAssignment saved = assignmentRepository.save(
                CourierAssignment.createNew(parcelId, newCourierId, trackingNo, reassignedAt));
        profileRepository.findById(newCourierId).ifPresent(profile -> {
            profile.incrementActiveDeliveries(clock.instant());
            profileRepository.save(profile);
        });
        cursor.record(saved.getId(), reassignedAt);
    }

    private void validateProofInput(UUID parcelId, AssignmentStatus status,
                                    DeliveryStatusUpdateRequest request, MultipartFile photo) {
        boolean hasKey = request.photoObjectKey() != null && !request.photoObjectKey().isBlank();
        if (status == AssignmentStatus.DELIVERED
                && (request.recipientName() == null || request.recipientName().isBlank())) {
            throw new BadRequestException("recipientName is required when status is DELIVERED");
        }
        if (photo != null && hasKey) {
            throw new BadRequestException("Supply a proof photo upload or an existing key, not both");
        }
        if (photo != null) {
            if (status != AssignmentStatus.DELIVERED) {
                throw new BadRequestException("Proof photo uploads require DELIVERED status");
            }
            try (var input = photo.getInputStream()) {
                ProofImageValidator.validate(photo.getContentType(), input, photo.getSize());
            } catch (IOException e) {
                throw new BadRequestException("Failed to read proof photo upload");
            }
        }
        if (hasKey) {
            String key = request.photoObjectKey();
            String prefix = "proofs/" + parcelId + "/";
            String suffix = key.startsWith(prefix) ? key.substring(prefix.length()) : "";
            if (!suffix.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)")
                    || !proofStorageService.exists(key)) {
                throw new BadRequestException("Proof key must identify an existing valid photo for this parcel");
            }
        }
        if (status == AssignmentStatus.DELIVERED && photo == null && !hasKey) {
            throw new BadRequestException("Proof photo is required when status is DELIVERED");
        }
    }

    private CourierAssignment currentOwnedAssignment(UUID courierId, UUID parcelId) {
        eventCursorRepository.ensureParcel(parcelId);
        AssignmentEventCursor cursor = eventCursorRepository.lockParcel(parcelId)
                .orElseThrow(() -> new ConflictException("Current assignment cannot be verified"));
        List<CourierAssignment> history = assignmentRepository.findByParcelId(parcelId);
        if (history.isEmpty()) {
            throw new NotFoundException("Delivery assignment not found for parcel " + parcelId);
        }
        List<CourierAssignment> current = history.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.REASSIGNED).toList();
        if (current.size() != 1) {
            throw new ConflictException("Current assignment ownership is ambiguous or unverifiable");
        }
        CourierAssignment assignment = current.get(0);
        if (!parcelId.equals(assignment.getParcelId())
                || (cursor.getAssignmentId() != null && !cursor.getAssignmentId().equals(assignment.getId()))) {
            throw new ConflictException("Assignment cursor conflicts with delivery history");
        }
        boolean newerClosedHistory = history.stream()
                .anyMatch(a -> a.getStatus() == AssignmentStatus.REASSIGNED && a.getCompletedAt() != null
                        && a.getCompletedAt().isAfter(assignment.getAssignedAt()));
        if (newerClosedHistory) {
            throw new ConflictException("Newer reassignment history prevents ownership verification");
        }
        if (!assignment.getCourierId().equals(courierId)) {
            throw new ForbiddenException("Parcel " + parcelId + " is assigned to another courier");
        }
        return assignment;
    }

    private AssignmentEventCursor lockEvent(String eventId, UUID parcelId, Instant occurredAt) {
        if (eventId == null || eventId.isBlank() || parcelId == null || occurredAt == null) {
            throw ambiguous("Event ID, parcel ID and timestamp are required");
        }
        eventCursorRepository.ensureParcel(parcelId);
        return eventCursorRepository.lockParcel(parcelId).orElseThrow();
    }

    private CourierAssignment currentAssignment(AssignmentEventCursor cursor, UUID parcelId) {
        if (cursor.getAssignmentId() != null) {
            return assignmentRepository.findById(cursor.getAssignmentId()).orElseThrow();
        }
        // Bootstrap pre-existing history conservatively, without choosing among conflicting rows.
        List<CourierAssignment> history = assignmentRepository.findByParcelId(parcelId);
        List<CourierAssignment> candidates = history.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.REASSIGNED).toList();
        if (history.isEmpty()) {
            return null;
        }
        if (candidates.size() != 1) {
            throw ambiguous("Existing assignment history has no unambiguous current record");
        }
        Instant latest = history.stream().map(a -> a.getStatus() == AssignmentStatus.REASSIGNED
                        && a.getCompletedAt() != null && a.getCompletedAt().isAfter(a.getAssignedAt())
                        ? a.getCompletedAt() : a.getAssignedAt())
                .max(Instant::compareTo).orElseThrow();
        CourierAssignment current = candidates.get(0);
        cursor.record(current.getId(), latest);
        return current;
    }

    private boolean isOlder(AssignmentEventCursor cursor, Instant occurredAt) {
        boolean older = cursor.getLastOccurredAt() != null && occurredAt.isBefore(cursor.getLastOccurredAt());
        if (older) {
            log.info("Ignoring assignment event older than persisted consumer progress");
        }
        return older;
    }

    private static AmqpRejectAndDontRequeueException ambiguous(String message) {
        return new AmqpRejectAndDontRequeueException(message);
    }
}
