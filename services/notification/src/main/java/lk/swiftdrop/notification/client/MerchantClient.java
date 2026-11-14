package lk.swiftdrop.notification.client;

import lk.swiftdrop.notification.client.dto.MerchantSummary;
import lk.swiftdrop.notification.config.AppProperties;
import org.springframework.stereotype.Component;

/** Who owns a merchant ({@code GET /internal/merchants/{merchantId}} on the Merchant Service, internal token). */
@Component
public class MerchantClient extends InternalServiceClient {

    public MerchantClient(AppProperties properties) {
        super("Merchant Service", properties.clients().merchantUrl(), properties);
    }

    public MerchantSummary merchant(String merchantId) {
        return get("/internal/merchants/{merchantId}", MerchantSummary.class, merchantId);
    }
}
