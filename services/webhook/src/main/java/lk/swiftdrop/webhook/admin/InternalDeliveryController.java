package lk.swiftdrop.webhook.admin;

import java.util.UUID;
import lk.swiftdrop.webhook.admin.dto.MerchantDelivery;
import lk.swiftdrop.webhook.common.PageRequests;
import lk.swiftdrop.webhook.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A merchant's delivery log for the Merchant Service portal ({@code DeliveryLogClient}). The {@code /internal/**}
 * security chain has already checked the internal token; never routed through the gateway.
 */
@RestController
@RequestMapping("/internal/merchants")
public class InternalDeliveryController {

    private final DeliveryQueryService queries;
    private final DeliveryMapper mapper;

    public InternalDeliveryController(DeliveryQueryService queries, DeliveryMapper mapper) {
        this.queries = queries;
        this.mapper = mapper;
    }

    @GetMapping("/{merchantId}/webhook-deliveries")
    public PageResponse<MerchantDelivery> deliveries(@PathVariable UUID merchantId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        return PageResponse.from(
                queries.forMerchant(merchantId, PageRequests.of(page, size, AdminDeliveryController.NEWEST_FIRST)),
                mapper::toMerchantDelivery);
    }
}
