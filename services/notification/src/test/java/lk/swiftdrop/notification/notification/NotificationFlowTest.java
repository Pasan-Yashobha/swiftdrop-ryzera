package lk.swiftdrop.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.notification.channel.OutgoingEmail;
import lk.swiftdrop.notification.client.dto.ContactDetails;
import lk.swiftdrop.notification.client.dto.MerchantSummary;
import lk.swiftdrop.notification.event.InvalidEventException;
import lk.swiftdrop.notification.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end behaviour of the consumer side on H2: events in, emails/SMS out (mocked channels), the notification log,
 * idempotency and the per-channel retry policy.
 */
class NotificationFlowTest extends AbstractIntegrationTest {

    private static final String CUSTOMER = "8f0c2a1e-1111-4a52-9d3e-2b8f0f1c6a11";
    private static final String OUT_FOR_DELIVERY_DATA = "{\"trackingNo\":\"SD24000123\",\"fromStatus\":\"IN_TRANSIT\","
            + "\"toStatus\":\"OUT_FOR_DELIVERY\",\"customerId\":\"" + CUSTOMER + "\",\"merchantId\":\"MER-031\"}";

    @Autowired
    private NotificationEventHandler handler;

    @Autowired
    private NotificationEventConsumer consumer;

    @Autowired
    private NotificationRetryJob retryJob;

    @BeforeEach
    void customerExists() {
        when(identityClient.contactOf(CUSTOMER))
                .thenReturn(new ContactDetails(CUSTOMER, "kalana@gmail.com", "Kalana", "0771234567"));
    }

    @Test
    void statusChangeSendsEmailAndSmsAndLogsBothMasked() {
        handler.handle(event(UUID.randomUUID(), "parcel.status.out_for_delivery", OUT_FOR_DELIVERY_DATA));

        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo("kalana@gmail.com");
        assertThat(email.getValue().subject()).isEqualTo("Parcel SD24000123 is out for delivery");
        assertThat(email.getValue().body()).contains("Hi Kalana,").contains("https://swiftdrop.test/track/SD24000123");
        verify(smsSender).send("0771234567",
                "SwiftDrop: Your parcel SD24000123 is out for delivery. Track it here: https://swiftdrop.test/track/SD24000123");

        List<Notification> log = notifications.findAll();
        assertThat(log).hasSize(2).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.getAttempts()).isEqualTo(1);
            assertThat(n.getCorrelationId()).startsWith("corr-");
        });
        assertThat(log).extracting(Notification::getRecipientMasked)
                .containsExactlyInAnyOrder("k***@gmail.com", "07X XXX 4567");
    }

    @Test
    void duplicateEventIsHandledOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        byte[] body = event(eventId, "parcel.status.out_for_delivery", OUT_FOR_DELIVERY_DATA);

        handler.handle(body);
        handler.handle(body);

        verify(emailSender, times(1)).send(any());
        verify(smsSender, times(1)).send(anyString(), anyString());
        assertThat(notifications.findAll()).hasSize(2);
        assertThat(processedEvents.count()).isEqualTo(1);
    }

    @Test
    void failedChannelIsRetriedAloneUntilItSucceeds() {
        doThrow(DeliveryException.temporary("SMS provider answered HTTP 503"))
                .doThrow(DeliveryException.temporary("SMS provider answered HTTP 503"))
                .doNothing()
                .when(smsSender).send(anyString(), anyString());

        handler.handle(event(UUID.randomUUID(), "parcel.status.out_for_delivery", OUT_FOR_DELIVERY_DATA));
        Notification sms = smsRow();
        assertThat(sms.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(sms.getAttempts()).isEqualTo(1);
        assertThat(sms.getNextAttemptAt()).isEqualTo(clock.instant().plusSeconds(30));

        // Not due yet: nothing happens.
        assertThat(retryJob.retryDue()).isZero();

        clock.advance(Duration.ofSeconds(30));
        assertThat(retryJob.retryDue()).isEqualTo(1);
        assertThat(smsRow().getStatus()).isEqualTo(NotificationStatus.RETRYING);

        clock.advance(Duration.ofSeconds(30));
        assertThat(retryJob.retryDue()).isEqualTo(1);
        sms = smsRow();
        assertThat(sms.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sms.getAttempts()).isEqualTo(3);
        assertThat(sms.getLastError()).isNull();

        // The email went out once, with the first attempt; only the SMS was repeated.
        verify(emailSender, times(1)).send(any());
        verify(smsSender, times(3)).send(anyString(), anyString());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void channelThatKeepsFailingEndsFailedAndIsCopiedToTheDeadLetterQueue() {
        doThrow(DeliveryException.temporary("SMS provider answered HTTP 503"))
                .when(smsSender).send(anyString(), anyString());

        handler.handle(event(UUID.randomUUID(), "parcel.status.out_for_delivery", OUT_FOR_DELIVERY_DATA));
        clock.advance(Duration.ofSeconds(30));
        retryJob.retryDue();
        clock.advance(Duration.ofSeconds(30));
        retryJob.retryDue();
        clock.advance(Duration.ofSeconds(30));
        assertThat(retryJob.retryDue()).isZero();

        Notification sms = smsRow();
        assertThat(sms.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(sms.getAttempts()).isEqualTo(3);
        assertThat(sms.getLastError()).isEqualTo("SMS provider answered HTTP 503");

        ArgumentCaptor<Message> dead = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("swiftdrop.dlx"), eq("notification.dlq"), dead.capture());
        MessageProperties properties = dead.getValue().getMessageProperties();
        assertThat((String) properties.getHeader(DeadLetterPublisher.HEADER_CHANNEL)).isEqualTo("SMS");
        assertThat(new String(dead.getValue().getBody(), StandardCharsets.UTF_8))
                .contains("parcel.status.out_for_delivery").doesNotContain("0771234567");
    }

    @Test
    void permanentFailureIsNotRetried() {
        String unknown = "00000000-0000-4000-8000-000000000000";
        when(identityClient.contactOf(unknown)).thenThrow(DeliveryException.permanent("Identity Service does not know this id (404)"));

        handler.handle(event(UUID.randomUUID(), "parcel.status.delivered",
                "{\"trackingNo\":\"SD1\",\"customerId\":\"" + unknown + "\"}"));

        assertThat(notifications.findAll()).hasSize(2).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(n.getAttempts()).isEqualTo(1);
        });
        verify(emailSender, never()).send(any());
    }

    @Test
    void smsIsSkippedWhenTheUserHasNoPhone() {
        when(identityClient.contactOf(CUSTOMER)).thenReturn(new ContactDetails(CUSTOMER, "k@x.lk", "K", null));

        handler.handle(event(UUID.randomUUID(), "parcel.status.out_for_delivery", OUT_FOR_DELIVERY_DATA));

        assertThat(smsRow().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(smsRow().getLastError()).isEqualTo("user has no phone number");
        verify(smsSender, never()).send(anyString(), anyString());
        verify(emailSender).send(any());
    }

    @Test
    void eventWithoutCustomerIsSkipped() {
        handler.handle(event(UUID.randomUUID(), "parcel.status.delivered", "{\"trackingNo\":\"SD1\"}"));

        assertThat(notifications.findAll()).hasSize(2)
                .allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SKIPPED));
    }

    @Test
    void eventTypeWithoutTemplatesIsRecordedButSendsNothing() {
        handler.handle(event(UUID.randomUUID(), "parcel.status.in_transit", OUT_FOR_DELIVERY_DATA));

        assertThat(notifications.count()).isZero();
        assertThat(processedEvents.count()).isEqualTo(1);
        verify(emailSender, never()).send(any());
    }

    @Test
    void receiptEmailCarriesThePdf() {
        byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
        when(documentClient.receiptPdf("RCPT-1")).thenReturn(pdf);

        handler.handle(event(UUID.randomUUID(), "receipt.generated", "{\"receiptId\":\"RCPT-1\",\"parcelId\":\"P1\","
                + "\"customerId\":\"" + CUSTOMER + "\",\"trackingNo\":\"SD9\",\"totalAmount\":\"1250.00\",\"currency\":\"LKR\"}"));

        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().attachment()).isEqualTo(pdf);
        assertThat(email.getValue().attachmentName()).isEqualTo("SwiftDrop-receipt-SD9.pdf");
        assertThat(email.getValue().body()).contains("LKR 1250.00");
    }

    @Test
    void receiptPdfFailureCountsAsAFailedEmailAttempt() {
        when(documentClient.receiptPdf("RCPT-2")).thenThrow(DeliveryException.temporary("Document Service answered HTTP 503"));

        handler.handle(event(UUID.randomUUID(), "receipt.generated",
                "{\"receiptId\":\"RCPT-2\",\"customerId\":\"" + CUSTOMER + "\"}"));

        Notification email = notifications.findAll().get(0);
        assertThat(email.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(email.getLastError()).contains("Document Service");
    }

    @Test
    void merchantEventsGoToTheMerchantOwner() {
        String owner = "6a1b2c3d-2222-4a52-9d3e-2b8f0f1c6a11";
        when(merchantClient.merchant("M-1")).thenReturn(new MerchantSummary("M-1", owner, "Kandy Crafts"));
        when(identityClient.contactOf(owner)).thenReturn(new ContactDetails(owner, "owner@crafts.lk", "Nimal", null));

        handler.handle(event(UUID.randomUUID(), "merchant.approved", "{\"merchantId\":\"M-1\",\"decidedBy\":\"admin-1\"}"));

        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo("owner@crafts.lk");
        assertThat(email.getValue().body()).contains("Kandy Crafts has been approved");
        assertThat(notifications.findAll().get(0).getRecipientRef()).isEqualTo(owner);
    }

    @Test
    void exhaustedWebhookEmailsTheMerchantWithTheOriginalEventType() {
        String owner = "6a1b2c3d-3333-4a52-9d3e-2b8f0f1c6a11";
        when(merchantClient.merchant("M-2")).thenReturn(new MerchantSummary("M-2", owner, "Galle Teas"));
        when(identityClient.contactOf(owner)).thenReturn(new ContactDetails(owner, "o@teas.lk", "Sunil", null));

        handler.handle(event(UUID.randomUUID(), "webhook.delivery.exhausted", "{\"deliveryId\":\"D1\","
                + "\"merchantId\":\"M-2\",\"eventId\":\"E1\",\"eventType\":\"parcel.status.delivered\","
                + "\"attempts\":6,\"lastHttpStatus\":503}"));

        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().body()).contains("the parcel.status.delivered webhook").contains("after 6 attempts")
                .contains("HTTP status was 503");
    }

    @Test
    void malformedMessagesAreRejectedWithoutRequeue() {
        assertThatThrownBy(() -> handler.handle("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);

        Message missingEventId = new Message("{\"eventType\":\"parcel.created\",\"data\":{}}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> consumer.onMessage(missingEventId))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("eventId");
        assertThat(processedEvents.count()).isZero();
    }

    @Test
    void consumerHandsValidMessagesToTheHandler() {
        doNothing().when(emailSender).send(any());
        consumer.onMessage(new Message(event(UUID.randomUUID(), "parcel.status.delivered", OUT_FOR_DELIVERY_DATA)));

        assertThat(notifications.count()).isEqualTo(2);
    }

    private Notification smsRow() {
        return notifications.findAll().stream().filter(n -> n.getChannel() == Channel.SMS).findFirst().orElseThrow();
    }
}
