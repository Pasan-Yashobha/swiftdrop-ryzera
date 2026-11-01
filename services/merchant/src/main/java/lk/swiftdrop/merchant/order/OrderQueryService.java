package lk.swiftdrop.merchant.order;

import java.util.UUID;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.common.PageResponse;
import lk.swiftdrop.merchant.order.dto.OrderDetailResponse;
import lk.swiftdrop.merchant.order.dto.OrderResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads orders. Every lookup is scoped to a merchant, so an order of another merchant is indistinguishable from
 * one that does not exist.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private static final String NOT_FOUND = "Order not found";

    private final MerchantOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderMapper mapper;

    OrderQueryService(MerchantOrderRepository orders, OrderStatusHistoryRepository history, OrderMapper mapper) {
        this.orders = orders;
        this.history = history;
        this.mapper = mapper;
    }

    public PageResponse<OrderResponse> list(UUID merchantId, OrderFilter filter, Pageable pageable) {
        return PageResponse.from(orders.findAll(OrderSpecifications.matching(merchantId, filter), pageable),
                mapper::toResponse);
    }

    public OrderDetailResponse get(UUID merchantId, UUID orderId) {
        MerchantOrder order = orders.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return mapper.toDetail(order, history.findByOrderIdOrderByOccurredAtAscIdAsc(order.getId()));
    }

    /** An order by tracking number, only if it lives in the given environment (live or sandbox). */
    public OrderResponse getByTrackingNo(UUID merchantId, String trackingNo, boolean sandbox) {
        return orders.findByMerchantIdAndTrackingNo(merchantId, trackingNo)
                .filter(order -> order.isSandbox() == sandbox)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }
}
