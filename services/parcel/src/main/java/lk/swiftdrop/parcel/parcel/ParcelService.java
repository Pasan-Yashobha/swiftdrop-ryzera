package lk.swiftdrop.parcel.parcel;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.parcel.common.BadRequestException;
import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.ForbiddenException;
import lk.swiftdrop.parcel.common.NotFoundException;
import lk.swiftdrop.parcel.common.PageResponse;
import lk.swiftdrop.parcel.event.OutboxService;
import lk.swiftdrop.parcel.idempotency.IdempotencyService;
import lk.swiftdrop.parcel.parcel.dto.*;
import lk.swiftdrop.parcel.pricing.PricingClient;
import lk.swiftdrop.parcel.pricing.PricingSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ParcelStateMachine stateMachine;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final PricingClient pricingClient;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public ParcelService(
            ParcelRepository parcelRepository,
            ParcelStatusHistoryRepository historyRepository,
            ParcelStateMachine stateMachine,
            TrackingNumberGenerator trackingNumberGenerator,
            PricingClient pricingClient,
            OutboxService outboxService,
            IdempotencyService idempotencyService,
            Clock clock) {
        this.parcelRepository = parcelRepository;
        this.historyRepository = historyRepository;
        this.stateMachine = stateMachine;
        this.trackingNumberGenerator = trackingNumberGenerator;
        this.pricingClient = pricingClient;
        this.outboxService = outboxService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Transactional
    public CustomerParcelDetailResponse createCustomerParcel(CreateCustomerParcelRequest request, String customerId) {
        return createCustomerParcel(request, customerId, null);
    }

    @Transactional
    public CustomerParcelDetailResponse createCustomerParcel(
            CreateCustomerParcelRequest request,
            String customerId,
            String idempotencyKey) {
        String scopedKey = resolveCustomerScopedKey(customerId, idempotencyKey);
        if (scopedKey != null) {
            Optional<CustomerParcelDetailResponse> cached = idempotencyService.startOrGetReplay(scopedKey, request,
                    CustomerParcelDetailResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        UUID parcelId = UUID.randomUUID();
        PricingSnapshot pricing = pricingClient.acceptQuote(request.quoteId(), parcelId);
        String trackingNumber = generateUniqueTrackingNumber();
        Instant now = clock.instant();

        Parcel parcel = Parcel.create(
                parcelId,
                trackingNumber,
                customerId,
                null,
                request.recipient().name(),
                request.recipient().phone(),
                request.recipient().email(),
                request.pickup().addressLine(),
                request.pickup().city(),
                request.pickup().postalCode(),
                request.dropoff().addressLine(),
                request.dropoff().city(),
                request.dropoff().postalCode(),
                request.weightKg(),
                request.size(),
                request.description(),
                request.deliveryType(),
                request.notes(),
                pricing,
                now);
        parcelRepository.save(parcel);

        ParcelStatusHistory history = new ParcelStatusHistory(
                UUID.randomUUID(), parcelId, 1, null, ParcelStatus.CREATED.name(),
                customerId, "CUSTOMER", "Parcel created by customer", now);
        historyRepository.save(history);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("parcelId", parcelId.toString());
        eventData.put("trackingNo", trackingNumber);
        eventData.put("customerId", customerId);
        outboxService.recordEvent("parcel.created", parcelId.toString(), eventData);

        CustomerParcelDetailResponse response = toDetailResponse(parcel, List.of(toHistoryDto(history)));

        if (scopedKey != null) {
            idempotencyService.recordCompleted(scopedKey, 201, response);
        }

        return response;
    }

    private String resolveCustomerScopedKey(String customerId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 120) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must not exceed 120 characters");
        }
        return "customer:" + customerId + ":" + trimmedKey;
    }

    @Transactional
    public InternalParcelBookingResponse createMerchantParcel(CreateMerchantParcelRequest request,
            String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<InternalParcelBookingResponse> cached = idempotencyService.startOrGetReplay(idempotencyKey,
                    request, InternalParcelBookingResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        UUID parcelId = UUID.randomUUID();
        PricingSnapshot pricing = pricingClient.acceptQuote(request.quoteId(), parcelId);
        String trackingNumber = generateUniqueTrackingNumber();
        Instant now = clock.instant();

        Parcel parcel = Parcel.create(
                parcelId,
                trackingNumber,
                null,
                request.merchantId(),
                request.recipient().name(),
                request.recipient().phone(),
                request.recipient().email(),
                request.pickup().addressLine(),
                request.pickup().city(),
                request.pickup().postalCode(),
                request.dropoff().addressLine(),
                request.dropoff().city(),
                request.dropoff().postalCode(),
                request.parcel().weightKg(),
                request.parcel().size(),
                request.parcel().description(),
                DeliveryType.STANDARD,
                request.notes(),
                pricing,
                now);
        parcelRepository.save(parcel);

        ParcelStatusHistory history = new ParcelStatusHistory(
                UUID.randomUUID(), parcelId, 1, null, ParcelStatus.CREATED.name(),
                request.merchantId().toString(), "MERCHANT", "Parcel created by merchant order", now);
        historyRepository.save(history);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("parcelId", parcelId.toString());
        eventData.put("trackingNo", trackingNumber);
        eventData.put("merchantId", request.merchantId().toString());
        outboxService.recordEvent("parcel.created", parcelId.toString(), eventData);

        var response = new InternalParcelBookingResponse(
                parcelId.toString(),
                trackingNumber,
                ParcelStatus.CREATED.name(),
                new MoneyDto(pricing.totalAmount(), pricing.currency()));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.recordCompleted(idempotencyKey, 201, response);
        }

        return response;
    }

    @Transactional
    public void updateCourierStatus(UUID parcelId, CourierStatusUpdateRequest request) {
        updateCourierStatus(parcelId, request, null);
    }

    @Transactional
    public void updateCourierStatus(UUID parcelId, CourierStatusUpdateRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyService.ReplayStatus replayStatus = idempotencyService.startOrCheckReplay(idempotencyKey,
                    new StatusUpdatePayload(parcelId.toString(), request));
            if (replayStatus == IdempotencyService.ReplayStatus.REPLAY) {
                return;
            }
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PARCEL_NOT_FOUND, "Parcel not found: " + parcelId));

        ParcelStatus fromStatus = parcel.getStatus();
        ParcelStatus toStatus = request.status();

        // 1. Courier assignment validation
        if (toStatus == ParcelStatus.ASSIGNED) {
            // Assignment transition: if already assigned to a DIFFERENT courier, reject
            if (parcel.getCourierId() != null && !parcel.getCourierId().isBlank()
                    && !parcel.getCourierId().equals(request.courierId())) {
                throw new ConflictException(ErrorCode.COURIER_MISMATCH,
                        "Courier '" + request.courierId() + "' cannot claim parcel " + parcelId
                                + " already assigned to courier '" + parcel.getCourierId() + "'");
            }
        } else {
            // Delivery status updates (PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED,
            // FAILED, RESCHEDULED, RETURNED):
            // Parcel must already be assigned, and assigned courier must match
            // request.courierId
            if (parcel.getCourierId() == null || parcel.getCourierId().isBlank()) {
                throw new ConflictException(ErrorCode.COURIER_MISMATCH,
                        "Cannot update delivery status for unassigned parcel " + parcelId
                                + "; parcel must be ASSIGNED first");
            }
            if (!parcel.getCourierId().equals(request.courierId())) {
                throw new ConflictException(ErrorCode.COURIER_MISMATCH,
                        "Courier '" + request.courierId() + "' is not assigned to parcel " + parcelId);
            }
        }

        // 2. Validate state transition
        stateMachine.validateTransition(fromStatus, toStatus);

        // 3. Repeated same-state check (idempotent no-op for repeated requests without
        // or with key)
        if (fromStatus == toStatus) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.recordCompleted(idempotencyKey, 204, null);
            }
            return;
        }

        // 4. Update parcel entity
        Instant now = clock.instant();
        parcel.updateStatus(toStatus, request.courierId(), request.recipientName(),
                request.photoObjectKey(), request.failureReason(), now);
        parcelRepository.save(parcel);

        // 5. Append status history
        int nextSeq = historyRepository.findMaxSequenceNumber(parcelId).orElse(0) + 1;
        ParcelStatusHistory history = new ParcelStatusHistory(
                UUID.randomUUID(), parcelId, nextSeq, fromStatus.name(), toStatus.name(),
                request.courierId(), "COURIER", request.note(), now);
        historyRepository.save(history);

        // 6. Record outbox event
        recordStatusEvent(parcel, fromStatus, toStatus, request.courierId(), request.failureReason(), now);

        // 7. Mark idempotency key completed if provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.recordCompleted(idempotencyKey, 204, null);
        }
    }

    record StatusUpdatePayload(String parcelId, CourierStatusUpdateRequest request) {
    }

    private void recordStatusEvent(Parcel parcel, ParcelStatus fromStatus, ParcelStatus toStatus,
            String courierId, String failureReason, Instant now) {
        String eventType;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parcelId", parcel.getId().toString());
        data.put("trackingNo", parcel.getTrackingNumber());

        if (toStatus == ParcelStatus.ASSIGNED) {
            eventType = "parcel.assigned";
            data.put("courierId", courierId);
            if (parcel.getMerchantId() != null) {
                data.put("merchantId", parcel.getMerchantId().toString());
            }
        } else if (toStatus == ParcelStatus.CANCELLED) {
            eventType = "parcel.cancelled";
            if (parcel.getMerchantId() != null) {
                data.put("merchantId", parcel.getMerchantId().toString());
            }
            if (parcel.getCustomerId() != null) {
                data.put("customerId", parcel.getCustomerId());
            }
            if (failureReason != null) {
                data.put("reason", failureReason);
            }
        } else {
            eventType = "parcel.status." + toStatus.name().toLowerCase(Locale.ROOT);
            data.put("fromStatus", fromStatus.name());
            data.put("toStatus", toStatus.name());
            data.put("courierId", courierId);
            if (parcel.getCustomerId() != null) {
                data.put("customerId", parcel.getCustomerId());
            }
            if (parcel.getMerchantId() != null) {
                data.put("merchantId", parcel.getMerchantId().toString());
            }
            if (failureReason != null) {
                data.put("failureReason", failureReason);
            }

            if (toStatus == ParcelStatus.DELIVERED) {
                data.put("deliveredAt", now.toString());
                Map<String, Object> pricingMap = new LinkedHashMap<>();
                pricingMap.put("baseAmount", parcel.getBaseAmount());
                pricingMap.put("weightAmount", parcel.getWeightAmount());
                pricingMap.put("sizeAmount", parcel.getSizeAmount());
                pricingMap.put("expressAmount", parcel.getExpressAmount());
                pricingMap.put("discountAmount", parcel.getDiscountAmount());
                pricingMap.put("totalAmount", parcel.getTotalFee());
                data.put("pricing", pricingMap);
                data.put("currency", parcel.getCurrency());
            }
        }

        outboxService.recordEvent(eventType, parcel.getId().toString(), data);
    }

    @Transactional(readOnly = true)
    public CustomerParcelDetailResponse getCustomerParcel(UUID parcelId, String customerId, boolean isAdmin) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PARCEL_NOT_FOUND, "Parcel not found: " + parcelId));

        if (!isAdmin && (parcel.getCustomerId() == null || !parcel.getCustomerId().equals(customerId))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "You do not have permission to view this parcel");
        }

        List<ParcelStatusHistoryDto> history = historyRepository.findByParcelIdOrderBySequenceNumberAsc(parcelId)
                .stream().map(this::toHistoryDto).toList();

        return toDetailResponse(parcel, history);
    }

    @Transactional
    public CustomerParcelDetailResponse cancelCustomerParcel(UUID parcelId, String customerId, boolean isAdmin,
            String reason) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PARCEL_NOT_FOUND, "Parcel not found: " + parcelId));

        if (!isAdmin && (parcel.getCustomerId() == null || !parcel.getCustomerId().equals(customerId))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "You do not have permission to cancel this parcel");
        }

        if (parcel.getStatus() == ParcelStatus.CANCELLED) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Parcel is already cancelled");
        }

        ParcelStatus fromStatus = parcel.getStatus();
        stateMachine.validateTransition(fromStatus, ParcelStatus.CANCELLED);

        Instant now = clock.instant();
        String cancelReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Cancelled by customer";
        parcel.updateStatus(ParcelStatus.CANCELLED, null, null, null, cancelReason, now);
        parcelRepository.save(parcel);

        int nextSeq = historyRepository.findMaxSequenceNumber(parcelId).orElse(0) + 1;
        ParcelStatusHistory history = new ParcelStatusHistory(
                UUID.randomUUID(), parcelId, nextSeq, fromStatus.name(), ParcelStatus.CANCELLED.name(),
                customerId, isAdmin ? "ADMIN" : "CUSTOMER", cancelReason, now);
        historyRepository.save(history);

        recordStatusEvent(parcel, fromStatus, ParcelStatus.CANCELLED, null, cancelReason, now);

        List<ParcelStatusHistoryDto> historyList = historyRepository.findByParcelIdOrderBySequenceNumberAsc(parcelId)
                .stream().map(this::toHistoryDto).toList();
        return toDetailResponse(parcel, historyList);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerParcelSummaryResponse> listCustomerParcels(
            String customerId, ParcelStatus status, String search, Pageable pageable) {
        String trimmed = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<Parcel> page;
        if (trimmed != null) {
            page = parcelRepository.findByCustomerFiltered(customerId, status, trimmed, pageable);
        } else if (status != null) {
            page = parcelRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        } else {
            page = parcelRepository.findByCustomerId(customerId, pageable);
        }
        return PageResponse.from(page, this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<UnassignedParcelResponse> getUnassignedParcels(ParcelStatus status) {
        ParcelStatus targetStatus = status != null ? status : ParcelStatus.CREATED;
        return parcelRepository.findByStatusOrderByCreatedAtAsc(targetStatus).stream()
                .map(p -> new UnassignedParcelResponse(
                        p.getId(),
                        p.getTrackingNumber(),
                        null,
                        p.getPickupCity(),
                        p.getSize() != null ? p.getSize().name() : null,
                        p.getWeightKg() != null ? p.getWeightKg().doubleValue() : 0.0,
                        p.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InternalParcelDetailResponse getInternalParcelDetails(UUID parcelId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PARCEL_NOT_FOUND, "Parcel not found: " + parcelId));

        var pricing = new InternalParcelDetailResponse.PriceBreakdownDto(
                parcel.getBaseAmount(),
                parcel.getWeightAmount(),
                parcel.getSizeAmount(),
                parcel.getExpressAmount(),
                parcel.getDiscountAmount(),
                parcel.getTotalFee());

        return new InternalParcelDetailResponse(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getCustomerId(),
                pricing,
                parcel.getCurrency());
    }

    @Transactional(readOnly = true)
    public PublicTrackingResponse getPublicTracking(String trackingNo) {
        Parcel parcel = parcelRepository.findByTrackingNumber(trackingNo)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PARCEL_NOT_FOUND,
                        "Tracking number not found: " + trackingNo));

        List<PublicTrackingResponse.TrackingMilestone> milestones = historyRepository
                .findByParcelIdOrderBySequenceNumberAsc(parcel.getId()).stream()
                .map(h -> new PublicTrackingResponse.TrackingMilestone(h.getToStatus(), h.getOccurredAt(),
                        h.getNotes()))
                .toList();

        return new PublicTrackingResponse(
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getPickupCity(),
                parcel.getDropoffCity(),
                parcel.getCreatedAt(),
                parcel.getDeliveredAt(),
                milestones);
    }

    private String generateUniqueTrackingNumber() {
        for (int i = 0; i < 5; i++) {
            String trackingNumber = trackingNumberGenerator.generate();
            if (!parcelRepository.existsByTrackingNumber(trackingNumber)) {
                return trackingNumber;
            }
        }
        throw new ConflictException(ErrorCode.CONFLICT, "Unable to generate a unique tracking number; please retry");
    }

    private CustomerParcelSummaryResponse toSummaryResponse(Parcel p) {
        return new CustomerParcelSummaryResponse(
                p.getId(),
                p.getTrackingNumber(),
                p.getStatus(),
                p.getRecipientName(),
                p.getPickupCity(),
                p.getDropoffCity(),
                p.getWeightKg(),
                p.getSize(),
                p.getTotalFee(),
                p.getCurrency(),
                p.getCreatedAt(),
                p.getDeliveredAt());
    }

    private CustomerParcelDetailResponse toDetailResponse(Parcel p, List<ParcelStatusHistoryDto> history) {
        var recipient = new RecipientDto(p.getRecipientName(), p.getRecipientPhone(), p.getRecipientEmail());
        var pickup = new AddressDto(p.getPickupAddressLine(), p.getPickupCity(), p.getPickupPostalCode());
        var dropoff = new AddressDto(p.getDropoffAddressLine(), p.getDropoffCity(), p.getDropoffPostalCode());
        var pricing = new CustomerParcelDetailResponse.PricingBreakdownDto(
                p.getQuoteId(),
                p.getBaseAmount(),
                p.getWeightAmount(),
                p.getSizeAmount(),
                p.getExpressAmount(),
                p.getDiscountAmount(),
                p.getTotalFee(),
                p.getCurrency());

        return new CustomerParcelDetailResponse(
                p.getId(),
                p.getTrackingNumber(),
                p.getStatus(),
                recipient,
                pickup,
                dropoff,
                p.getWeightKg(),
                p.getSize(),
                p.getDeliveryType(),
                p.getDescription(),
                p.getNotes(),
                pricing,
                p.getCourierId(),
                p.getFailureReason(),
                p.getDeliveredAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                history);
    }

    private ParcelStatusHistoryDto toHistoryDto(ParcelStatusHistory h) {
        return new ParcelStatusHistoryDto(
                h.getSequenceNumber(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getChangedBy(),
                h.getActorRole(),
                h.getNotes(),
                h.getOccurredAt());
    }
}
