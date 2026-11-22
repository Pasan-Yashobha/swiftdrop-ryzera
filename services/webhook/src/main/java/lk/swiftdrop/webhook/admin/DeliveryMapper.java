package lk.swiftdrop.webhook.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.webhook.admin.dto.DeliveryDetail;
import lk.swiftdrop.webhook.admin.dto.DeliverySummary;
import lk.swiftdrop.webhook.admin.dto.MerchantDelivery;
import lk.swiftdrop.webhook.delivery.WebhookDelivery;
import lk.swiftdrop.webhook.delivery.WebhookDeliveryAttempt;
import org.springframework.stereotype.Component;

/** Entity to response mapping of the delivery endpoints. */
@Component
public class DeliveryMapper {

    private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public DeliveryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeliverySummary toSummary(WebhookDelivery d) {
        return new DeliverySummary(d.getId(), d.getMerchantId(), d.getEventId(), d.getEventType(), d.getUrl(),
                d.getStatus(), d.getAttempts(), d.getLastHttpStatus(), d.getDurationMs(), d.getCreatedAt(),
                d.getLastAttemptAt(), d.getNextAttemptAt(), d.getError(), d.getReplayCount());
    }

    public DeliveryDetail toDetail(WebhookDelivery d, List<WebhookDeliveryAttempt> attempts) {
        List<DeliveryDetail.Attempt> log = attempts.stream()
                .map(a -> new DeliveryDetail.Attempt(a.getAttemptNo(), a.getAttemptedAt(), a.getHttpStatus(),
                        a.getDurationMs(), a.getOutcome(), a.getError()))
                .toList();
        return new DeliveryDetail(d.getId(), d.getMerchantId(), d.getEventId(), d.getEventType(), d.getUrl(),
                d.getStatus(), d.getAttempts(), d.getLastHttpStatus(), d.getDurationMs(), d.getCreatedAt(),
                d.getLastAttemptAt(), d.getNextAttemptAt(), d.getError(), d.getReplayCount(),
                headers(d.getLastRequestHeaders()), d.getPayload(), d.getResponseSnippet(), d.getLastReplayedBy(), log);
    }

    public MerchantDelivery toMerchantDelivery(WebhookDelivery d) {
        return new MerchantDelivery(d.getId(), d.getEventId(), d.getEventType(), d.getStatus(), d.getAttempts(),
                d.getLastHttpStatus(), d.getDurationMs(), d.getCreatedAt(), d.getLastAttemptAt(), d.getError());
    }

    private Map<String, String> headers(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, HEADERS);
        } catch (JsonProcessingException unreadable) {
            return Map.of();
        }
    }
}
