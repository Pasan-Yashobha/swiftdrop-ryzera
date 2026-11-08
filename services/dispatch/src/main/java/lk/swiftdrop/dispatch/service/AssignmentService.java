package lk.swiftdrop.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.dispatch.client.ParcelClient;
import lk.swiftdrop.dispatch.common.ConflictException;
import lk.swiftdrop.dispatch.common.ErrorCode;
import lk.swiftdrop.dispatch.common.NotFoundException;
import lk.swiftdrop.dispatch.common.UnprocessableEntityException;
import lk.swiftdrop.dispatch.domain.Assignment;
import lk.swiftdrop.dispatch.domain.AssignmentStatus;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.OutboxEvent;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.AssignmentResponse;
import lk.swiftdrop.dispatch.dto.AutoAssignResponse;
import lk.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import lk.swiftdrop.dispatch.repository.AssignmentRepository;
import lk.swiftdrop.dispatch.repository.CourierWorkloadRepository;
import lk.swiftdrop.dispatch.repository.OutboxEventRepository;
import lk.swiftdrop.dispatch.strategy.AssignmentStrategy;
import lk.swiftdrop.dispatch.strategy.AssignmentStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final AssignmentRepository assignmentRepository;
    private final CourierWorkloadRepository workloadRepository;
    private final OutboxEventRepository outboxRepository;
    private final AssignmentStrategyFactory strategyFactory;
    private final ParcelClient parcelClient;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             CourierWorkloadRepository workloadRepository,
                             OutboxEventRepository outboxRepository,
                             AssignmentStrategyFactory strategyFactory,
                             ParcelClient parcelClient,
                             SseEmitterService sseEmitterService,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.assignmentRepository = assignmentRepository;
        this.workloadRepository = workloadRepository;
        this.outboxRepository = outboxRepository;
        this.strategyFactory = strategyFactory;
        this.parcelClient = parcelClient;
        this.sseEmitterService = sseEmitterService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public AssignmentResponse assignManual(UUID parcelId, UUID courierId, UUID assignedBy) {
        // 1. Guard against double-assignment
        if (assignmentRepository.existsByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE)) {
            throw new ConflictException(ErrorCode.PARCEL_ALREADY_ASSIGNED,
                    "Parcel " + parcelId + " is already assigned to a courier");
        }

        // 2. Lock courier with SELECT ... FOR UPDATE
        CourierWorkload workload = workloadRepository.findByIdForUpdate(courierId)
                .orElseThrow(() -> new NotFoundException("Courier " + courierId + " not found in workload registry"));

        // 3. Check capacity and availability
        if (!workload.isAvailable()) {
            throw new UnprocessableEntityException(ErrorCode.COURIER_UNAVAILABLE,
                    "Courier " + courierId + " is currently offline/unavailable");
        }
        if (workload.getActiveDeliveries() >= workload.getMaxActive()) {
            throw new UnprocessableEntityException(ErrorCode.COURIER_CAPACITY_EXCEEDED,
                    "Courier " + courierId + " has reached maximum active delivery capacity (" + workload.getMaxActive() + ")");
        }

        // 4. Create active assignment
        Instant now = Instant.now();
        Assignment assignment = new Assignment(UUID.randomUUID(), parcelId, courierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, assignedBy, now, null);
        assignmentRepository.save(assignment);

        // 5. Update courier workload
        workload.incrementActive(now);
        CourierWorkload savedWorkload = workloadRepository.save(workload);

        // 6. Record outbox event for reliable publishing
        String payload = createEventPayload(Map.of(
                "parcelId", parcelId.toString(),
                "courierId", courierId.toString(),
                "strategy", "MANUAL",
                "assignedBy", assignedBy.toString()
        ));
        outboxRepository.save(OutboxEvent.create("parcel.assigned", parcelId, payload));

        // 7. Update clients & broadcast
        parcelClient.removeMockParcel(parcelId);
        sseEmitterService.broadcast("assignment.created", AssignmentResponse.from(assignment));
        sseEmitterService.broadcast("workload.changed", CourierWorkloadResponse.from(savedWorkload));

        log.info("Manually assigned parcel {} to courier {} by {}", parcelId, courierId, assignedBy);
        return AssignmentResponse.from(assignment);
    }

    public AutoAssignResponse autoAssign(List<UUID> parcelIds, StrategyType strategyType, UUID assignedBy) {
        if (parcelIds == null || parcelIds.isEmpty()) {
            return new AutoAssignResponse(List.of(), List.of());
        }

        AssignmentStrategy strategy = strategyFactory.getStrategy(strategyType);

        // Fetch parcels to order oldest first
        List<UnassignedParcel> parcels = parcelIds.stream()
                .map(parcelClient::getParcel)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(UnassignedParcel::createdAt))
                .toList();

        List<AutoAssignResponse.AssignedItem> assigned = new ArrayList<>();
        List<AutoAssignResponse.SkippedItem> skipped = new ArrayList<>();

        for (UnassignedParcel parcel : parcels) {
            // Process each parcel in its own transaction so one failure does not roll back others
            Boolean success = transactionTemplate.execute(status -> {
                try {
                    // Check if already assigned
                    if (assignmentRepository.existsByParcelIdAndStatus(parcel.parcelId(), AssignmentStatus.ACTIVE)) {
                        skipped.add(new AutoAssignResponse.SkippedItem(parcel.parcelId(), "Parcel already assigned"));
                        return false;
                    }

                    // Retrieve available couriers
                    List<CourierWorkload> couriers = workloadRepository.findByAvailableTrue();
                    Optional<CourierWorkload> chosen = strategy.selectCourier(parcel, couriers);

                    if (chosen.isEmpty()) {
                        skipped.add(new AutoAssignResponse.SkippedItem(parcel.parcelId(),
                                "No eligible courier found under " + strategyType + " strategy"));
                        return false;
                    }

                    // Lock chosen courier
                    UUID chosenCourierId = chosen.get().getCourierId();
                    CourierWorkload lockedWorkload = workloadRepository.findByIdForUpdate(chosenCourierId)
                            .orElse(null);

                    if (lockedWorkload == null || !lockedWorkload.isEligible()) {
                        skipped.add(new AutoAssignResponse.SkippedItem(parcel.parcelId(),
                                "Courier capacity changed concurrently, skipped"));
                        return false;
                    }

                    // Save assignment and increment load
                    Instant now = Instant.now();
                    Assignment assignment = new Assignment(UUID.randomUUID(), parcel.parcelId(), chosenCourierId,
                            strategyType, AssignmentStatus.ACTIVE, assignedBy, now, null);
                    assignmentRepository.save(assignment);

                    lockedWorkload.incrementActive(now);
                    CourierWorkload savedWorkload = workloadRepository.save(lockedWorkload);

                    // Outbox event
                    String payload = createEventPayload(Map.of(
                            "parcelId", parcel.parcelId().toString(),
                            "courierId", chosenCourierId.toString(),
                            "strategy", strategyType.name(),
                            "assignedBy", assignedBy.toString()
                    ));
                    outboxRepository.save(OutboxEvent.create("parcel.assigned", parcel.parcelId(), payload));

                    parcelClient.removeMockParcel(parcel.parcelId());
                    sseEmitterService.broadcast("assignment.created", AssignmentResponse.from(assignment));
                    sseEmitterService.broadcast("workload.changed", CourierWorkloadResponse.from(savedWorkload));

                    assigned.add(new AutoAssignResponse.AssignedItem(parcel.parcelId(), chosenCourierId, strategyType.name()));
                    return true;
                } catch (Exception ex) {
                    log.warn("Auto-assign failed for parcel {}: {}", parcel.parcelId(), ex.getMessage());
                    skipped.add(new AutoAssignResponse.SkippedItem(parcel.parcelId(), "Error: " + ex.getMessage()));
                    status.setRollbackOnly();
                    return false;
                }
            });
        }

        return new AutoAssignResponse(assigned, skipped);
    }

    @Transactional
    public AssignmentResponse reassign(UUID parcelId, UUID newCourierId, String reason, UUID assignedBy) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new lk.swiftdrop.dispatch.common.BadRequestException("Reason for reassignment is mandatory");
        }

        // 1. Locate current active assignment
        Assignment active = assignmentRepository.findByParcelIdAndStatus(parcelId, AssignmentStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("No active assignment found for parcel " + parcelId));

        UUID oldCourierId = active.getCourierId();
        if (oldCourierId.equals(newCourierId)) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "New courier cannot be the same as the current assigned courier");
        }

        // 2. Lock new courier and verify capacity
        CourierWorkload newCourierWorkload = workloadRepository.findByIdForUpdate(newCourierId)
                .orElseThrow(() -> new NotFoundException("Target courier " + newCourierId + " not found"));

        if (!newCourierWorkload.isAvailable()) {
            throw new UnprocessableEntityException(ErrorCode.COURIER_UNAVAILABLE, "Target courier is unavailable");
        }
        if (newCourierWorkload.getActiveDeliveries() >= newCourierWorkload.getMaxActive()) {
            throw new UnprocessableEntityException(ErrorCode.COURIER_CAPACITY_EXCEEDED,
                    "Target courier capacity exceeded (max " + newCourierWorkload.getMaxActive() + ")");
        }

        // 3. Lock old courier and decrement active deliveries
        CourierWorkload oldCourierWorkload = workloadRepository.findByIdForUpdate(oldCourierId).orElse(null);
        if (oldCourierWorkload != null) {
            oldCourierWorkload.decrementActive();
            workloadRepository.save(oldCourierWorkload);
            sseEmitterService.broadcast("workload.changed", CourierWorkloadResponse.from(oldCourierWorkload));
        }

        // 4. Mark old assignment as REASSIGNED
        active.reassign(reason);
        assignmentRepository.save(active);

        // 5. Create new ACTIVE assignment
        Instant now = Instant.now();
        Assignment newAssignment = new Assignment(UUID.randomUUID(), parcelId, newCourierId,
                StrategyType.MANUAL, AssignmentStatus.ACTIVE, assignedBy, now, "Reassigned from " + oldCourierId + ": " + reason);
        assignmentRepository.save(newAssignment);

        // 6. Increment new courier load
        newCourierWorkload.incrementActive(now);
        CourierWorkload savedNewWorkload = workloadRepository.save(newCourierWorkload);

        // 7. Write outbox event
        String payload = createEventPayload(Map.of(
                "parcelId", parcelId.toString(),
                "oldCourierId", oldCourierId.toString(),
                "newCourierId", newCourierId.toString(),
                "reason", reason,
                "reassignedBy", assignedBy.toString()
        ));
        outboxRepository.save(OutboxEvent.create("parcel.reassigned", parcelId, payload));

        // 8. Broadcast live update
        sseEmitterService.broadcast("assignment.reassigned", AssignmentResponse.from(newAssignment));
        sseEmitterService.broadcast("workload.changed", CourierWorkloadResponse.from(savedNewWorkload));

        log.info("Reassigned parcel {} from courier {} to {} by {}: {}",
                parcelId, oldCourierId, newCourierId, assignedBy, reason);
        return AssignmentResponse.from(newAssignment);
    }

    public List<UnassignedParcel> getUnassignedParcels(String zone, String size, int page, int limit) {
        List<UnassignedParcel> raw = parcelClient.getUnassignedParcels(zone, size, page, limit);
        // Exclude any parcel that has an ACTIVE assignment row in database
        return raw.stream()
                .filter(p -> !assignmentRepository.existsByParcelIdAndStatus(p.parcelId(), AssignmentStatus.ACTIVE))
                .toList();
    }

    public List<CourierWorkloadResponse> getCourierWorkloads() {
        List<CourierWorkload> workloads = workloadRepository.findAll();
        if (workloads.isEmpty()) {
            seedDefaultCouriers();
            workloads = workloadRepository.findAll();
        }
        return workloads.stream()
                .map(CourierWorkloadResponse::from)
                .toList();
    }

    public List<AssignmentResponse> getAssignments(UUID parcelId, UUID courierId) {
        if (parcelId != null && courierId != null) {
            return assignmentRepository.findByParcelIdAndCourierIdOrderByAssignedAtDesc(parcelId, courierId)
                    .stream().map(AssignmentResponse::from).toList();
        }
        if (parcelId != null) {
            return assignmentRepository.findByParcelIdOrderByAssignedAtDesc(parcelId)
                    .stream().map(AssignmentResponse::from).toList();
        }
        if (courierId != null) {
            return assignmentRepository.findByCourierIdOrderByAssignedAtDesc(courierId)
                    .stream().map(AssignmentResponse::from).toList();
        }
        return assignmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Assignment::getAssignedAt).reversed())
                .map(AssignmentResponse::from)
                .toList();
    }

    private void seedDefaultCouriers() {
        UUID zoneColombo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID zoneKandy = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID zoneGalle = UUID.fromString("00000000-0000-0000-0000-000000000003");

        workloadRepository.save(new CourierWorkload(
                UUID.fromString("c0000001-0000-0000-0000-000000000001"),
                zoneColombo, 2, 8, true, Instant.now().minusSeconds(1200)));
        workloadRepository.save(new CourierWorkload(
                UUID.fromString("c0000002-0000-0000-0000-000000000002"),
                zoneColombo, 4, 8, true, Instant.now().minusSeconds(300)));
        workloadRepository.save(new CourierWorkload(
                UUID.fromString("c0000003-0000-0000-0000-000000000003"),
                zoneKandy, 1, 8, true, Instant.now().minusSeconds(2400)));
        workloadRepository.save(new CourierWorkload(
                UUID.fromString("c0000004-0000-0000-0000-000000000004"),
                zoneGalle, 7, 8, true, Instant.now().minusSeconds(100)));
        workloadRepository.save(new CourierWorkload(
                UUID.fromString("c0000005-0000-0000-0000-000000000005"),
                zoneColombo, 0, 8, false, null)); // Offline courier
    }

    private String createEventPayload(Map<String, String> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

