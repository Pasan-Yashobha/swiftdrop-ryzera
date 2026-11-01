package lk.swiftdrop.merchant.order;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.merchant.MerchantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates orders for the portal, the merchant API and the bulk upload (CONTRACT section 6).
 *
 * <p>Creation runs outside of any transaction and refuses to run inside one. A {@code PENDING} placeholder is
 * committed first, so the unique constraints on the idempotency key and the external reference decide between
 * concurrent duplicates; the parcel service is called with no transaction open; then the placeholder is
 * completed, or removed when booking failed so that the same key can be used again.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** A conflicting row can vanish between the violation and the lookup (its creator failed); try again. */
    private static final int MAX_RESERVE_ATTEMPTS = 3;

    private final MerchantService merchants;
    private final MerchantOrderRepository orders;
    private final OrderWriter writer;
    private final ParcelClient parcelClient;
    private final SandboxSimulator simulator;
    private final OrderMapper mapper;
    private final Clock clock;

    OrderService(MerchantService merchants, MerchantOrderRepository orders, OrderWriter writer,
                 ParcelClient parcelClient, SandboxSimulator simulator, OrderMapper mapper, Clock clock) {
        this.merchants = merchants;
        this.orders = orders;
        this.writer = writer;
        this.parcelClient = parcelClient;
        this.simulator = simulator;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @throws ForbiddenException      {@code MERCHANT_NOT_APPROVED} unless the merchant is APPROVED right now
     * @throws UnprocessableException  {@code IDEMPOTENCY_KEY_REUSED}, {@code PARCEL_REJECTED}
     * @throws ConflictException       {@code IDEMPOTENCY_KEY_IN_USE}, {@code DUPLICATE_EXTERNAL_REF}
     * @throws lk.swiftdrop.merchant.common.ServiceUnavailableException {@code PARCEL_SERVICE_UNAVAILABLE}
     */
    @Transactional(propagation = Propagation.NEVER)
    public OrderCreation create(NewOrder command) {
        requireApproved(command.merchantId());
        String fingerprint = RequestFingerprint.of(command.request(), command.sandbox());
        for (int attempt = 0; attempt < MAX_RESERVE_ATTEMPTS; attempt++) {
            Optional<MerchantOrder> replay = findExisting(command, fingerprint);
            if (replay.isPresent()) {
                return new OrderCreation(mapper.toResponse(replay.get()), true);
            }
            Optional<MerchantOrder> placeholder = reserve(command, fingerprint);
            if (placeholder.isPresent()) {
                MerchantOrder order = fulfil(placeholder.get(), command);
                log.info("Order {} created for merchant {} (source {}, sandbox {})", order.getId(),
                        order.getMerchantId(), order.getSource(), order.isSandbox());
                return new OrderCreation(mapper.toResponse(order), false);
            }
        }
        throw new ConflictException(ErrorCode.INVALID_STATE, "The order could not be reserved. Please try again");
    }

    /** The merchant is read again on every call: an approval can be withdrawn after the caller authenticated. */
    private void requireApproved(UUID merchantId) {
        if (!merchants.getById(merchantId).isApproved()) {
            throw new ForbiddenException(ErrorCode.MERCHANT_NOT_APPROVED,
                    "Your merchant account must be approved before you can create orders");
        }
    }

    /**
     * Looks for the order this request repeats or collides with.
     *
     * <p>The order that holds the external reference is looked at too: a concurrent request with the same key may
     * have inserted its placeholder between the lookup by key and the lookup by reference. That order is a replay
     * (or still in flight), not a duplicate reference.
     *
     * @return the order to replay, empty when the key and the external reference are unused
     */
    private Optional<MerchantOrder> findExisting(NewOrder command, String fingerprint) {
        String key = command.idempotencyKey();
        if (key != null) {
            Optional<MerchantOrder> sameKey = orders.findByMerchantIdAndIdempotencyKey(command.merchantId(), key);
            if (sameKey.isPresent()) {
                return Optional.of(checkedReplay(sameKey.get(), fingerprint));
            }
        }
        String externalRef = command.request().externalRef();
        if (externalRef == null) {
            return Optional.empty();
        }
        Optional<MerchantOrder> sameReference = orders.findByMerchantIdAndExternalRef(command.merchantId(), externalRef);
        if (sameReference.isPresent() && key != null && key.equals(sameReference.get().getIdempotencyKey())) {
            return Optional.of(checkedReplay(sameReference.get(), fingerprint));
        }
        if (sameReference.isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_EXTERNAL_REF,
                    "Another order of this merchant already uses this externalRef");
        }
        return Optional.empty();
    }

    private static MerchantOrder checkedReplay(MerchantOrder existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestHash())) {
            throw new UnprocessableException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request");
        }
        if (existing.getStatus() == OrderStatus.PENDING) {
            throw new IdempotencyKeyInUseException();
        }
        return existing;
    }

    /** @return the committed placeholder, empty when a concurrent request took the key or the reference first */
    private Optional<MerchantOrder> reserve(NewOrder command, String fingerprint) {
        MerchantOrder placeholder = MerchantOrder.placeholder(UUID.randomUUID(), command.merchantId(),
                command.request(), command.source(), command.sandbox(), command.idempotencyKey(), fingerprint,
                clock.instant());
        try {
            return Optional.of(writer.insertPlaceholder(placeholder));
        } catch (DataIntegrityViolationException conflict) {
            // Which constraint was violated is found by looking at the rows, not by parsing the database message.
            return Optional.empty();
        }
    }

    private MerchantOrder fulfil(MerchantOrder placeholder, NewOrder command) {
        ParcelBooking booking = command.sandbox() ? simulate(placeholder) : book(placeholder, command);
        return writer.complete(placeholder.getId(), booking.parcelId(), booking.trackingNo(), booking.fee(),
                clock.instant());
    }

    private ParcelBooking simulate(MerchantOrder placeholder) {
        String trackingNo;
        do {
            trackingNo = simulator.newTrackingNo();
        } while (orders.existsByTrackingNo(trackingNo));
        return new ParcelBooking(null, trackingNo, simulator.feeFor(placeholder.getWeightKg()));
    }

    private ParcelBooking book(MerchantOrder placeholder, NewOrder command) {
        try {
            return parcelClient.createParcel(
                    new ParcelBookingRequest(placeholder.getId(), command.merchantId(), command.request()));
        } catch (RuntimeException failure) {
            writer.discard(placeholder.getId());
            throw failure;
        }
    }
}
