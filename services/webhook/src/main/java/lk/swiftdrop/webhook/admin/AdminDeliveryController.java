package lk.swiftdrop.webhook.admin;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lk.swiftdrop.webhook.admin.dto.DeliveryDetail;
import lk.swiftdrop.webhook.admin.dto.DeliverySummary;
import lk.swiftdrop.webhook.common.ApiError;
import lk.swiftdrop.webhook.common.BadRequestException;
import lk.swiftdrop.webhook.common.PageRequests;
import lk.swiftdrop.webhook.common.PageResponse;
import lk.swiftdrop.webhook.delivery.DeliveryService;
import lk.swiftdrop.webhook.delivery.DeliveryStatus;
import lk.swiftdrop.webhook.delivery.WebhookDelivery;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin console API of the delivery log (ROLE_ADMIN, enforced by the security configuration). */
@RestController
@RequestMapping("/api/v1/admin/webhooks/deliveries")
public class AdminDeliveryController {

    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final DeliveryQueryService queries;
    private final DeliveryService deliveryService;
    private final DeliveryMapper mapper;

    public AdminDeliveryController(DeliveryQueryService queries, DeliveryService deliveryService,
                                   DeliveryMapper mapper) {
        this.queries = queries;
        this.deliveryService = deliveryService;
        this.mapper = mapper;
    }

    @GetMapping
    public PageResponse<DeliverySummary> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String merchantId,
                                              @RequestParam(required = false) String eventType,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        DeliveryQueryService.Filter filter = new DeliveryQueryService.Filter(parseStatus(status),
                parseMerchantId(merchantId), StringUtils.hasText(eventType) ? eventType.trim() : null);
        return PageResponse.from(queries.list(filter, PageRequests.of(page, size, NEWEST_FIRST)), mapper::toSummary);
    }

    @GetMapping("/{id}")
    public DeliveryDetail detail(@PathVariable UUID id) {
        WebhookDelivery delivery = queries.get(id);
        return mapper.toDetail(delivery, queries.attempts(id));
    }

    /** One immediate attempt of a FAILED delivery with a fresh timestamp and signature. */
    @PostMapping("/{id}/replay")
    public ResponseEntity<DeliverySummary> replay(@PathVariable UUID id, Principal principal) {
        WebhookDelivery delivery = deliveryService.replay(id, principal.getName());
        return ResponseEntity.accepted().body(mapper.toSummary(delivery));
    }

    private static DeliveryStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return DeliveryStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw invalid("status", "must be one of PENDING, RETRYING, DELIVERED, FAILED");
        }
    }

    private static UUID parseMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        try {
            return UUID.fromString(merchantId.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw invalid("merchantId", "must be a UUID");
        }
    }

    private static BadRequestException invalid(String field, String message) {
        return new BadRequestException("Invalid filter", List.of(new ApiError.Detail(field, message)));
    }
}
