package lk.swiftdrop.notification.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lk.swiftdrop.notification.client.DocumentClient;
import lk.swiftdrop.notification.client.IdentityClient;
import lk.swiftdrop.notification.client.MerchantClient;
import lk.swiftdrop.notification.client.dto.ContactDetails;
import lk.swiftdrop.notification.client.dto.MerchantSummary;
import lk.swiftdrop.notification.common.ContactMasker;
import lk.swiftdrop.notification.config.AppProperties;
import lk.swiftdrop.notification.event.EventEnvelope;
import lk.swiftdrop.notification.event.InvalidEventException;
import lk.swiftdrop.notification.template.Audience;
import lk.swiftdrop.notification.template.NotificationTemplate;
import lk.swiftdrop.notification.template.NotificationTemplateRepository;
import lk.swiftdrop.notification.template.TemplateRenderer;
import org.springframework.stereotype.Component;

/**
 * Turns a stored notification into a ready-to-send message: finds the recipient (Identity, and the Merchant Service
 * for merchant-facing templates), renders the current template wording and, for receipts, fetches the PDF.
 * Runs on every attempt, so a retry or resend always uses fresh contact details and the latest template.
 */
@Component
public class MessageComposer {

    static final String RECEIPT_EVENT = "receipt.generated";

    private final NotificationTemplateRepository templates;
    private final IdentityClient identity;
    private final MerchantClient merchants;
    private final DocumentClient documents;
    private final ObjectMapper objectMapper;
    private final String trackingUrl;

    public MessageComposer(NotificationTemplateRepository templates, IdentityClient identity, MerchantClient merchants,
                           DocumentClient documents, ObjectMapper objectMapper, AppProperties properties) {
        this.templates = templates;
        this.identity = identity;
        this.merchants = merchants;
        this.documents = documents;
        this.objectMapper = objectMapper;
        this.trackingUrl = stripTrailingSlash(properties.publicTrackingUrl());
    }

    /**
     * @throws DeliveryException when a service needed for the message fails (temporary or permanent)
     */
    public ComposedMessage compose(Notification notification) {
        EventEnvelope event;
        try {
            event = EventEnvelope.parse(notification.getEnvelope().getBytes(StandardCharsets.UTF_8), objectMapper);
        } catch (InvalidEventException e) {
            throw DeliveryException.permanent("stored event cannot be read: " + e.getMessage());
        }
        NotificationTemplate template = templates.findById(notification.getTemplateId())
                .orElseThrow(() -> DeliveryException.permanent("template " + notification.getTemplateKey()
                        + " no longer exists"));

        Map<String, String> values = new HashMap<>();
        values.put("eventType", event.eventType());
        values.put("eventId", event.eventId().toString());
        event.data().fields().forEachRemaining(field -> {
            if (field.getValue().isValueNode() && !field.getValue().isNull()) {
                values.put(field.getKey(), field.getValue().asText());
            }
        });
        String trackingNo = event.dataText("trackingNo");
        if (trackingNo != null) {
            values.put("trackingLink", trackingUrl + "/" + trackingNo);
        }

        String userId;
        if (template.getAudience() == Audience.MERCHANT) {
            String merchantId = event.dataText("merchantId");
            if (merchantId == null) {
                return ComposedMessage.skip(null, "event has no merchantId");
            }
            MerchantSummary merchant = merchants.merchant(merchantId);
            values.put("businessName", merchant.businessName() == null ? "" : merchant.businessName());
            userId = merchant.ownerUserId();
            if (userId == null) {
                return ComposedMessage.skip(merchantId, "merchant has no owner user");
            }
        } else {
            userId = event.dataText("customerId");
            if (userId == null) {
                return ComposedMessage.skip(null, "event has no customerId");
            }
        }

        ContactDetails contact = identity.contactOf(userId);
        values.put("recipientName", contact.fullName() == null || contact.fullName().isBlank()
                ? "there" : contact.fullName());
        String subject = TemplateRenderer.render(template.getSubject(), values);
        String body = TemplateRenderer.render(template.getBody(), values);

        if (notification.getChannel() == Channel.SMS) {
            if (contact.phone() == null || contact.phone().isBlank()) {
                return ComposedMessage.skip(userId, "user has no phone number");
            }
            return ComposedMessage.sms(userId, ContactMasker.maskPhone(contact.phone()), contact.phone(), body);
        }
        if (contact.email() == null || contact.email().isBlank()) {
            return ComposedMessage.skip(userId, "user has no email address");
        }
        byte[] attachment = null;
        String attachmentName = null;
        if (RECEIPT_EVENT.equals(event.eventType())) {
            String receiptId = event.dataText("receiptId") != null ? event.dataText("receiptId") : event.aggregateId();
            attachment = documents.receiptPdf(receiptId);
            attachmentName = "SwiftDrop-receipt-" + (trackingNo != null ? trackingNo : receiptId) + ".pdf";
        }
        return ComposedMessage.email(userId, ContactMasker.maskEmail(contact.email()), contact.email(), subject, body,
                attachmentName, attachment);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
