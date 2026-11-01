package lk.swiftdrop.merchant.order;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ApiError;
import lk.swiftdrop.merchant.common.BadRequestException;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.PageRequests;
import lk.swiftdrop.merchant.common.PageResponse;
import lk.swiftdrop.merchant.merchant.CurrentMerchantProvider;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.order.dto.BulkResult;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.OrderDetailResponse;
import lk.swiftdrop.merchant.order.dto.OrderResponse;
import lk.swiftdrop.merchant.order.dto.OrderStats;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The orders of the signed-in merchant user (CONTRACT section 5.4). Reading needs a registered merchant, so that a
 * suspended merchant can still see its history; creating orders needs an approved one.
 */
@RestController
@RequestMapping(MerchantOrderController.PATH)
public class MerchantOrderController {

    static final String PATH = "/api/v1/merchants/me/orders";

    private static final String TEMPLATE_FILE_NAME = "swiftdrop-orders-template.csv";
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final CurrentMerchantProvider currentMerchant;
    private final OrderService orderService;
    private final OrderQueryService queries;
    private final OrderStatsService stats;
    private final BulkCsvImporter bulkImporter;

    MerchantOrderController(CurrentMerchantProvider currentMerchant, OrderService orderService,
                            OrderQueryService queries, OrderStatsService stats, BulkCsvImporter bulkImporter) {
        this.currentMerchant = currentMerchant;
        this.orderService = orderService;
        this.queries = queries;
        this.stats = stats;
        this.bulkImporter = bulkImporter;
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sandbox,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        SandboxMode mode = SandboxMode.parse(sandbox).orElseThrow(() -> new BadRequestException("Validation failed",
                List.of(new ApiError.Detail("sandbox", "must be true, false or all"))));
        OrderFilter filter = OrderFilter.forPortal(status, Inputs.trimToNull(search), mode, from, to);
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return queries.list(currentMerchant.requireMerchant().getId(), filter, pageable);
    }

    @GetMapping("/stats")
    public OrderStats stats() {
        return stats.statsFor(currentMerchant.requireMerchant().getId());
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable UUID id) {
        return queries.get(currentMerchant.requireMerchant().getId(), id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestParam(defaultValue = "false") boolean sandbox,
            @RequestHeader(name = OrderCreationHttp.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        Merchant merchant = currentMerchant.requireApprovedMerchant();
        OrderCreation creation = orderService.create(new NewOrder(merchant.getId(), request, OrderSource.PORTAL,
                sandbox, OrderCreationHttp.idempotencyKey(idempotencyKey)));
        return OrderCreationHttp.response(creation, PATH, creation.order().id().toString());
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkResult bulk(@RequestParam("file") MultipartFile file) {
        return bulkImporter.importOrders(currentMerchant.requireApprovedMerchant().getId(), file);
    }

    @GetMapping("/bulk/template")
    public ResponseEntity<String> bulkTemplate() {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEMPLATE_FILE_NAME + "\"")
                .body(bulkImporter.template());
    }
}
