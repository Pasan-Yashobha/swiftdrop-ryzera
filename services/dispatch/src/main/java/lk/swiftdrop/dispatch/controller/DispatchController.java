package lk.swiftdrop.dispatch.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.dispatch.dto.AssignmentResponse;
import lk.swiftdrop.dispatch.dto.AutoAssignRequest;
import lk.swiftdrop.dispatch.dto.AutoAssignResponse;
import lk.swiftdrop.dispatch.dto.CourierWorkloadResponse;
import lk.swiftdrop.dispatch.dto.ManualAssignRequest;
import lk.swiftdrop.dispatch.dto.ReassignRequest;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import lk.swiftdrop.dispatch.security.CurrentUser;
import lk.swiftdrop.dispatch.service.AssignmentService;
import lk.swiftdrop.dispatch.service.IdempotencyService;
import lk.swiftdrop.dispatch.service.SseEmitterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final AssignmentService assignmentService;
    private final IdempotencyService idempotencyService;
    private final SseEmitterService sseEmitterService;

    public DispatchController(AssignmentService assignmentService,
                              IdempotencyService idempotencyService,
                              SseEmitterService sseEmitterService) {
        this.assignmentService = assignmentService;
        this.idempotencyService = idempotencyService;
        this.sseEmitterService = sseEmitterService;
    }

    /**
     * Unassigned parcel queue: oldest first, with zone and size filters.
     */
    @GetMapping("/parcels/unassigned")
    public List<UnassignedParcel> getUnassignedParcels(
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String size,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        return assignmentService.getUnassignedParcels(zone, size, page, limit);
    }

    /**
     * Courier workload board: active, maxActive, availability, and zone.
     */
    @GetMapping("/couriers/workload")
    public List<CourierWorkloadResponse> getCourierWorkloads() {
        return assignmentService.getCourierWorkloads();
    }

    /**
     * Manual assignment: assigns a specific parcel to a specific courier.
     */
    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResponse> assignManual(
            @Valid @RequestBody ManualAssignRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Object cached = idempotencyService.get(idempotencyKey);
            if (cached instanceof AssignmentResponse cachedResp) {
                return ResponseEntity.status(HttpStatus.CREATED).body(cachedResp);
            }
        }

        UUID dispatcherId = CurrentUser.current().userId();
        AssignmentResponse response = assignmentService.assignManual(
                request.parcelId(),
                request.courierId(),
                dispatcherId
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.put(idempotencyKey, response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Auto-assign: assigns a list of parcels using the selected assignment strategy.
     */
    @PostMapping("/assignments/auto")
    public ResponseEntity<AutoAssignResponse> autoAssign(
            @Valid @RequestBody AutoAssignRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Object cached = idempotencyService.get(idempotencyKey);
            if (cached instanceof AutoAssignResponse cachedResp) {
                return ResponseEntity.ok(cachedResp);
            }
        }

        UUID dispatcherId = CurrentUser.current().userId();
        AutoAssignResponse response = assignmentService.autoAssign(
                request.parcelIds(),
                request.strategy(),
                dispatcherId
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.put(idempotencyKey, response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Reassign parcel to a different courier with a mandatory audit reason.
     */
    @PostMapping("/assignments/{parcelId}/reassign")
    public AssignmentResponse reassign(
            @PathVariable UUID parcelId,
            @Valid @RequestBody ReassignRequest request) {
        UUID dispatcherId = CurrentUser.current().userId();
        return assignmentService.reassign(
                parcelId,
                request.newCourierId(),
                request.reason(),
                dispatcherId
        );
    }

    /**
     * Assignment history filtered by parcel and/or courier.
     */
    @GetMapping("/assignments")
    public List<AssignmentResponse> getAssignments(
            @RequestParam(required = false) UUID parcelId,
            @RequestParam(required = false) UUID courierId) {
        return assignmentService.getAssignments(parcelId, courierId);
    }

    /**
     * Server-Sent Events stream for live updates on the Dispatcher Console.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseEmitterService.subscribe();
    }
}
