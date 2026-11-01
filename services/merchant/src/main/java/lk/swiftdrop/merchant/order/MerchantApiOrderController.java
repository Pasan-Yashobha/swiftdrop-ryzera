package lk.swiftdrop.merchant.order;

import jakarta.validation.Valid;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.common.PageRequests;
import lk.swiftdrop.merchant.common.PageResponse;
import lk.swiftdrop.merchant.credential.MerchantApiPaths;
import lk.swiftdrop.merchant.order.dto.AdvanceOrderRequest;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.OrderResponse;
import lk.swiftdrop.merchant.security.MerchantApiPrincipal;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orders for integrators (CONTRACT section 5.4). Authentication, the rate limit, the approval check and the scope
 * check ({@code orders:write} for POST, {@code orders:read} for GET) are done by the merchant API security chain;
 * the principal says which merchant and which environment the credential belongs to. A TEST credential only ever
 * sees and creates sandbox orders, a LIVE credential only live ones.
 */
@RestController
@RequestMapping(MerchantApiPaths.BASE)
public class MerchantApiOrderController {

    private static final String ORDERS_PATH = MerchantApiPaths.BASE + "/orders";

    private final OrderService orderService;
    private final OrderQueryService queries;
    private final SandboxOrderService sandboxOrders;

    MerchantApiOrderController(OrderService orderService, OrderQueryService queries,
                               SandboxOrderService sandboxOrders) {
        this.orderService = orderService;
        this.queries = queries;
        this.sandboxOrders = sandboxOrders;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal MerchantApiPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = OrderCreationHttp.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        OrderCreation creation = orderService.create(new NewOrder(principal.merchantId(), request, OrderSource.API,
                principal.isSandbox(), OrderCreationHttp.idempotencyKey(idempotencyKey)));
        return OrderCreationHttp.response(creation, ORDERS_PATH, creation.order().trackingNo());
    }

    @GetMapping("/orders/{trackingNo}")
    public OrderResponse get(@AuthenticationPrincipal MerchantApiPrincipal principal,
                             @PathVariable String trackingNo) {
        return queries.getByTrackingNo(principal.merchantId(), trackingNo, principal.isSandbox());
    }

    @GetMapping("/orders")
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal MerchantApiPrincipal principal,
            @RequestParam(required = false) String externalRef,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        OrderFilter filter = OrderFilter.forApi(status, externalRef, principal.isSandbox());
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return queries.list(principal.merchantId(), filter, pageable);
    }

    @PostMapping("/sandbox/orders/{trackingNo}/advance")
    public OrderResponse advance(@AuthenticationPrincipal MerchantApiPrincipal principal,
                                 @PathVariable String trackingNo,
                                 @Valid @RequestBody(required = false) AdvanceOrderRequest request) {
        if (!principal.isSandbox()) {
            throw new ForbiddenException(ErrorCode.SANDBOX_ONLY,
                    "Orders can only be advanced with a TEST credential");
        }
        return sandboxOrders.advance(principal.merchantId(), trackingNo,
                request == null ? AdvanceOrderRequest.next() : request);
    }
}
