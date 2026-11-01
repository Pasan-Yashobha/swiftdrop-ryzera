package lk.swiftdrop.merchant.order;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.merchant.MerchantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Creates orders for the portal, the merchant API and the bulk upload (CONTRACT section 6).
 *
 * <p>Creation runs outside of any transaction and refuses to run inside one. A {@code PENDING} placeholder is
 * committed first, so the unique constraints on the idempotency key and the external reference decide between
 * concurrent duplicates; the parcel service is called with no transaction open; then the placeholder is
 * completed.
 *
 * <p>When the parcel service rejects the order for good (4xx), the placeholder is removed so that the key and the
 * external reference can be used again. When the outcome is unknown (timeout, 5xx, lost answer) the parcel may have
 * been booked, so the placeholder stays. The parcel service de-duplicates on the placeholder's id, which is sent as
 * its Idempotency-Key; a retry with the same key (or, without a key, the same external reference and request)
 * therefore completes <em>that</em> placeholder once it is old enough for its first request to be over, instead of
 * booking a second parcel.
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
    private final Duration resumeAfter;

    OrderService(MerchantService merchants, MerchantOrderRepository orders, OrderWriter writer,
                 ParcelClient parcelClient, SandboxSimulator simulator, OrderMapper mapper, Clock clock,
                 AppProperties properties) {
        this.merchants = merchants;
        this.orders = orders;
        this.writer = writer;
        this.parcelClient = parcelClient;
        this.simulator = simulator;
        this.mapper = mapper;
        this.clock = clock;
        this.resumeAfter = Duration.ofSeconds(properties.orders().resumeAfterSeconds());
    }

    /**
     * @throws ForbiddenException      {@code MERCHANT_NOT_APPROVED} unless the merchant is APPROVED right now
     * @throws UnprocessableException  {@code IDEMPOTENCY_KEY_REUSED}, {@code PARCEL_REJECTED}
     * @throws ConflictException       {@code IDEMPOTENCY_KEY_IN_USE}, {@code DUPLICATE_EXTERNAL_REF}
     * @throws lk.swiftdrop.merchant.common.ServiceUnavailableException {@code PARCEL_SERVICE_UNAVAILABLE}
     */
    public OrderCreation create(NewOrder command) {
        // Not @Transactional(NEVER): that opens a transaction synchronization, which binds one EntityManager (and its
        // JDBC connection) to the thread for the whole call, including the parcel service call.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Order creation must not run inside a transaction");
        }
        requireApproved(command.merchantId());
        String fingerprint = RequestFingerprint.of(command.request(), command.sandbox());
        for (int attempt = 0; attempt < MAX_RESERVE_ATTEMPTS; attempt++) {
            Optional<MerchantOrder> existing = findExisting(command, fingerprint);
            if (existing.isPresent()) {
                MerchantOrder order = existing.get();
                if (order.getStatus() != OrderStatus.PENDING) {
                    return new OrderCreation(mapper.toResponse(order), true);
                }
                if (order.getCreatedAt().isAfter(clock.instant().minus(resumeAfter))) {
                    throw new IdempotencyKeyInUseException();
                }
                log.info("Completing order {} left PENDING by an earlier request", order.getId());
                return new OrderCreation(mapper.toResponse(fulfil(order, command)), false);
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
     * @return the order to replay or, when it is still {@code PENDING}, to complete; empty when the key and the
     *         external reference are unused
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
        Optional<MerchantOrder> sameReference = orders.findByMerchantIdAndSandboxAndExternalRef(
                command.merchantId(), command.sandbox(), externalRef);
        if (sameReference.isEmpty()) {
            return Optional.empty();
        }
        MerchantOrder holder = sameReference.get();
        if (key != null && key.equals(holder.getIdempotencyKey())) {
            return Optional.of(checkedReplay(holder, fingerprint));
        }
        // Without a key a repeat is recognised only while it is unfinished: the identical request that left a
        // placeholder behind is the retry of that order, not a second order with the same reference.
        boolean retryWithoutKey = key == null && holder.getIdempotencyKey() == null
                && holder.getStatus() == OrderStatus.PENDING && fingerprint.equals(holder.getRequestHash());
        if (retryWithoutKey) {
            return sameReference;
        }
        throw new ConflictException(ErrorCode.DUPLICATE_EXTERNAL_REF,
                "Another order of this merchant already uses this externalRef");
    }

    private static MerchantOrder checkedReplay(MerchantOrder existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestHash())) {
            throw new UnprocessableException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request");
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
        } catch (UnprocessableException rejected) {
            // The parcel service refused the order: nothing was booked, so the key and the reference are free again.
            writer.discard(placeholder.getId());
            throw rejected;
        }
        // Any other failure leaves the placeholder: the parcel may have been booked (see the class comment).
    }
}
