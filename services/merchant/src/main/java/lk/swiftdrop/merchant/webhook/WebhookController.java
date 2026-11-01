package lk.swiftdrop.merchant.webhook;

import jakarta.validation.Valid;
import lk.swiftdrop.merchant.common.PageRequests;
import lk.swiftdrop.merchant.webhook.dto.SaveWebhookRequest;
import lk.swiftdrop.merchant.webhook.dto.WebhookDeliveryPage;
import lk.swiftdrop.merchant.webhook.dto.WebhookSecretResponse;
import lk.swiftdrop.merchant.webhook.dto.WebhookTestResult;
import lk.swiftdrop.merchant.webhook.dto.WebhookView;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A merchant's webhook settings (CONTRACT section 5.5). Every endpoint works on the caller's own merchant. */
@RestController
@RequestMapping("/api/v1/merchants/me/webhook")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public WebhookView get() {
        return webhookService.findMine()
                .<WebhookView>map(WebhookMapper::toView)
                .orElseGet(WebhookView.NotConfigured::new);
    }

    /** The response carries the secret only when this call created the configuration. */
    @PutMapping
    public WebhookView.Configured save(@Valid @RequestBody SaveWebhookRequest request) {
        WebhookService.SaveResult saved = webhookService.save(request.url(), request.active(), request.events());
        WebhookView.Configured view = WebhookMapper.toView(saved.config());
        return saved.secret() == null ? view : view.withSecret(saved.secret());
    }

    @PostMapping("/rotate-secret")
    public WebhookSecretResponse rotateSecret() {
        return new WebhookSecretResponse(webhookService.rotateSecret());
    }

    @PostMapping("/test")
    public WebhookTestResult test() {
        return webhookService.test();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete() {
        webhookService.delete();
    }

    @GetMapping("/deliveries")
    public WebhookDeliveryPage deliveries(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return webhookService.deliveries(PageRequests.of(page, size, Sort.unsorted()));
    }
}
