package lk.swiftdrop.notification.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import lk.swiftdrop.notification.client.dto.ContactDetails;
import lk.swiftdrop.notification.notification.DeliveryException;
import lk.swiftdrop.notification.notification.Notification;
import lk.swiftdrop.notification.notification.NotificationEventHandler;
import lk.swiftdrop.notification.security.Role;
import lk.swiftdrop.notification.sms.MockSmsMessage;
import lk.swiftdrop.notification.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.notification.testsupport.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class NotificationAdminApiTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/notifications";
    private static final String CUSTOMER = "8f0c2a1e-1111-4a52-9d3e-2b8f0f1c6a11";
    private static final String SMS_TEMPLATE_ID = "0a3c1f6e-0004-4000-8000-000000000004";
    private static final String EMAIL_TEMPLATE_ID = "0a3c1f6e-0003-4000-8000-000000000003";

    @Autowired
    private NotificationEventHandler handler;

    @BeforeEach
    void customerExists() {
        when(identityClient.contactOf(CUSTOMER))
                .thenReturn(new ContactDetails(CUSTOMER, "kalana@gmail.com", "Kalana", "0771234567"));
    }

    private void outForDelivery() {
        handler.handle(event(UUID.randomUUID(), "parcel.status.out_for_delivery",
                "{\"trackingNo\":\"SD24000123\",\"customerId\":\"" + CUSTOMER + "\"}"));
    }

    @Test
    void requiresAnAdmin() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(BASE).with(TestAuth.withRole("u1", Role.MERCHANT))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listsTheLogWithFiltersAndMaskedRecipients() throws Exception {
        outForDelivery();

        mockMvc.perform(get(BASE).with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get(BASE).param("channel", "SMS").param("status", "SENT")
                        .param("eventType", "parcel.status.out_for_delivery")
                        .param("from", "2026-11-14T00:00:00Z").param("to", "2026-11-15T00:00:00Z")
                        .with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].recipient").value("07X XXX 4567"))
                .andExpect(jsonPath("$.content[0].templateKey").value("parcel.status.out_for_delivery.SMS"))
                .andExpect(jsonPath("$.content[0].attempts").value(1));

        mockMvc.perform(get(BASE).param("to", "2026-11-14T00:00:00Z").with(TestAuth.admin("admin-1")))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(BASE).param("size", "500").with(TestAuth.admin("admin-1")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE).param("status", "NOPE").with(TestAuth.admin("admin-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendsAFailedNotification() throws Exception {
        doThrow(DeliveryException.permanent("SMS provider rejected the number"))
                .when(smsSender).send(anyString(), anyString());
        outForDelivery();
        Notification failed = notifications.findAll().stream()
                .filter(n -> n.getStatus().name().equals("FAILED")).findFirst().orElseThrow();
        Notification sent = notifications.findAll().stream()
                .filter(n -> n.getStatus().name().equals("SENT")).findFirst().orElseThrow();

        doNothing().when(smsSender).send(anyString(), anyString());
        mockMvc.perform(post(BASE + "/" + failed.getId() + "/resend").with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.attempts").value(2));

        mockMvc.perform(post(BASE + "/" + sent.getId() + "/resend").with(TestAuth.admin("admin-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/resend").with(TestAuth.admin("admin-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void resendThatFailsAgainStaysFailed() throws Exception {
        doThrow(DeliveryException.temporary("SMS provider answered HTTP 503"))
                .when(smsSender).send(anyString(), anyString());
        outForDelivery();
        Notification sms = notifications.findAll().stream()
                .filter(n -> n.getChannel().name().equals("SMS")).findFirst().orElseThrow();
        // Force it to FAILED by exhausting the retries.
        for (int i = 0; i < 2; i++) {
            clock.advance(java.time.Duration.ofMinutes(1));
            retry();
        }
        mockMvc.perform(post(BASE + "/" + sms.getId() + "/resend").with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(4));
    }

    @Autowired
    private lk.swiftdrop.notification.notification.NotificationRetryJob retryJob;

    private void retry() {
        retryJob.retryDue();
    }

    @Test
    void listsAndEditsTemplates() throws Exception {
        mockMvc.perform(get(BASE + "/templates").with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(13))));

        mockMvc.perform(put(BASE + "/templates/" + SMS_TEMPLATE_ID).with(TestAuth.admin("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"ignored for sms\",\"body\":\"SwiftDrop: {{trackingNo}} arrives today\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andExpect(jsonPath("$.body").value("SwiftDrop: {{trackingNo}} arrives today"));

        mockMvc.perform(put(BASE + "/templates/" + EMAIL_TEMPLATE_ID).with(TestAuth.admin("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"  \",\"body\":\"Body\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // The new wording is used at once and the inactive template sends nothing.
        outForDelivery();
        org.mockito.Mockito.verify(smsSender).send("0771234567", "SwiftDrop: SD24000123 arrives today");
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(put(BASE + "/templates/" + SMS_TEMPLATE_ID).with(TestAuth.admin("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"\",\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(put(BASE + "/templates/" + UUID.randomUUID()).with(TestAuth.admin("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\",\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void showsTheMockSmsInbox() throws Exception {
        smsMessages.save(new MockSmsMessage(UUID.randomUUID(), "07X XXX 4567", "hello", clock.instant()));

        mockMvc.perform(get(BASE + "/sms-inbox").with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].to").value("07X XXX 4567"))
                .andExpect(jsonPath("$.content[0].message").value("hello"));
    }

    @Test
    void otherPathsAreDenied() throws Exception {
        mockMvc.perform(get("/api/v1/something").with(TestAuth.admin("admin-1"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
