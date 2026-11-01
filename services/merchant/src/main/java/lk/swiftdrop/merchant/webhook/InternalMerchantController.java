package lk.swiftdrop.merchant.webhook;

import java.util.UUID;
import lk.swiftdrop.merchant.common.NotFoundException;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantService;
import lk.swiftdrop.merchant.webhook.dto.InternalMerchantResponse;
import lk.swiftdrop.merchant.webhook.dto.InternalWebhookResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lookups for other SwiftDrop services (CONTRACT section 5.6). The {@code /internal/**} security chain
 * has already checked the internal token; these endpoints are never routed through the gateway. An id that is
 * not a UUID simply names no merchant.
 */
@RestController
@RequestMapping("/internal/merchants")
public class InternalMerchantController {

    private final WebhookService webhookService;
    private final MerchantService merchantService;

    public InternalMerchantController(WebhookService webhookService, MerchantService merchantService) {
        this.webhookService = webhookService;
        this.merchantService = merchantService;
    }

    /** Includes the decrypted signing secret, so intermediaries must not store the response. */
    @GetMapping("/{merchantId}/webhook")
    public ResponseEntity<InternalWebhookResponse> webhook(@PathVariable String merchantId) {
        WebhookConfig config = webhookService.findByMerchantId(parse(merchantId))
                .orElseThrow(() -> new NotFoundException("No webhook is configured for this merchant"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(WebhookMapper.toInternal(config, webhookService.revealSecret(config)));
    }

    @GetMapping("/{merchantId}")
    public InternalMerchantResponse merchant(@PathVariable String merchantId) {
        Merchant merchant = merchantService.getById(parse(merchantId));
        return new InternalMerchantResponse(merchant.getId(), merchant.getOwnerUserId(), merchant.getBusinessName(),
                merchant.getStatus());
    }

    private static UUID parse(String merchantId) {
        try {
            return UUID.fromString(merchantId);
        } catch (IllegalArgumentException notAUuid) {
            throw new NotFoundException("Merchant not found");
        }
    }
}
