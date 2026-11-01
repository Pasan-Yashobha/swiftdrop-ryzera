package lk.swiftdrop.merchant.order;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.order.dto.AdvanceOrderRequest;
import lk.swiftdrop.merchant.order.dto.AdvanceOutcome;
import lk.swiftdrop.merchant.order.dto.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves sandbox orders through their delivery on request, standing in for the couriers. Nothing is published to
 * the broker and no webhook is sent.
 */
@Service
@Transactional
public class SandboxOrderService {

    private static final Logger log = LoggerFactory.getLogger(SandboxOrderService.class);

    private static final String DEFAULT_FAILURE_REASON = "Delivery failed (sandbox)";

    private final MerchantOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderMapper mapper;
    private final Clock clock;

    SandboxOrderService(MerchantOrderRepository orders, OrderStatusHistoryRepository history, OrderMapper mapper,
                        Clock clock) {
        this.orders = orders;
        this.history = history;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Takes the next delivery step, or fails the order.
     *
     * @throws NotFoundException {@code NOT_FOUND} unless the merchant has a sandbox order with this tracking number
     * @throws ConflictException {@code INVALID_STATE} when the order is delivered, failed, cancelled or returned
     */
    public OrderResponse advance(UUID merchantId, String trackingNo, AdvanceOrderRequest request) {
        MerchantOrder order = orders.findByMerchantIdAndTrackingNo(merchantId, trackingNo)
                .filter(MerchantOrder::isSandbox)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        OrderStatus next = order.getStatus().nextInSandboxLifecycle().orElseThrow(() -> new ConflictException(
                ErrorCode.INVALID_STATE, "An order that is " + order.getStatus() + " cannot be advanced"));
        boolean failing = request.outcome() == AdvanceOutcome.FAILED;
        OrderStatus target = failing ? OrderStatus.FAILED : next;
        String reason = failing ? Objects.requireNonNullElse(request.reason(), DEFAULT_FAILURE_REASON) : null;

        Instant now = clock.instant();
        history.save(OrderStatusHistory.record(order.getId(), null, order.getStatus(), target, now, reason));
        order.moveTo(target, reason, now, now);
        log.info("Sandbox order {} moved to {}", order.getId(), target);
        return mapper.toResponse(order);
    }
}
