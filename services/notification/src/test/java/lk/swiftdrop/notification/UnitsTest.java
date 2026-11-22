package lk.swiftdrop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.notification.channel.OutgoingEmail;
import lk.swiftdrop.notification.channel.SmtpEmailSender;
import lk.swiftdrop.notification.common.ContactMasker;
import lk.swiftdrop.notification.config.AppProperties;
import lk.swiftdrop.notification.event.EventEnvelope;
import lk.swiftdrop.notification.event.InvalidEventException;
import lk.swiftdrop.notification.notification.DeliveryException;
import lk.swiftdrop.notification.template.TemplateRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class UnitsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rendersPlaceholdersAndBlanksUnknownOnes() {
        String out = TemplateRenderer.render("Hi {{ name }}, parcel {{trackingNo}} {{missing}}$1\\",
                Map.of("name", "Kalana", "trackingNo", "SD1"));
        assertThat(out).isEqualTo("Hi Kalana, parcel SD1 $1\\");
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
        assertThat(TemplateRenderer.render("{{x}}", Map.of("x", "a$b{{y}}"))).isEqualTo("a$b{{y}}");
    }

    @Test
    void masksContactDetails() {
        assertThat(ContactMasker.maskEmail("kalana@gmail.com")).isEqualTo("k***@gmail.com");
        assertThat(ContactMasker.maskEmail("@x")).isEqualTo("***");
        assertThat(ContactMasker.maskEmail(null)).isEqualTo("***");
        assertThat(ContactMasker.maskPhone("0771234567")).isEqualTo("07X XXX 4567");
        assertThat(ContactMasker.maskPhone("+94 77 123 4567")).isEqualTo("07X XXX 4567");
        assertThat(ContactMasker.maskPhone("+4412345678901")).isEqualTo("*** 8901");
        assertThat(ContactMasker.maskPhone("123")).isEqualTo("***");
        assertThat(ContactMasker.maskPhone(null)).isEqualTo("***");
    }

    private static byte[] json(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesAValidEnvelope() {
        EventEnvelope event = EventEnvelope.parse(json("{\"eventId\":\"7c1e9a52-0f4b-4a52-9d3e-2b8f0f1c6a11\","
                + "\"eventType\":\"parcel.status.delivered\",\"version\":1,\"occurredAt\":\"2026-11-01T08:30:12Z\","
                + "\"producer\":\"parcel-service\",\"correlationId\":\"c1\",\"aggregateId\":\"P1\","
                + "\"data\":{\"trackingNo\":\"SD1\",\"etaMinutes\":45,\"nested\":{\"a\":1},\"nothing\":null}}"),
                objectMapper);
        assertThat(event.eventType()).isEqualTo("parcel.status.delivered");
        assertThat(event.dataText("trackingNo")).isEqualTo("SD1");
        assertThat(event.dataText("etaMinutes")).isEqualTo("45");
        assertThat(event.dataText("nested")).isNull();
        assertThat(event.dataText("nothing")).isNull();
        assertThat(event.dataText("absent")).isNull();
    }

    @Test
    void rejectsBrokenEnvelopes() {
        String ok = "\"eventType\":\"x\",\"version\":1,\"occurredAt\":\"2026-11-01T08:30:12Z\",\"producer\":\"p\","
                + "\"correlationId\":\"c\",\"aggregateId\":\"a\",\"data\":{}";
        String id = "\"eventId\":\"7c1e9a52-0f4b-4a52-9d3e-2b8f0f1c6a11\",";
        assertThatThrownBy(() -> EventEnvelope.parse(json("{"), objectMapper)).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> EventEnvelope.parse(json("[1]"), objectMapper)).hasMessageContaining("not a JSON object");
        assertThatThrownBy(() -> EventEnvelope.parse(json("{\"eventId\":\"nope\"," + ok + "}"), objectMapper))
                .hasMessageContaining("not a UUID");
        assertThatThrownBy(() -> EventEnvelope.parse(json("{" + id + ok.replace("\"version\":1", "\"version\":0") + "}"),
                objectMapper)).hasMessageContaining("version");
        assertThatThrownBy(() -> EventEnvelope.parse(json("{" + id + ok.replace("2026-11-01T08:30:12Z", "yesterday") + "}"),
                objectMapper)).hasMessageContaining("occurredAt");
        assertThatThrownBy(() -> EventEnvelope.parse(json("{" + id + ok.replace("\"data\":{}", "\"data\":[]") + "}"),
                objectMapper)).hasMessageContaining("data");
        assertThatThrownBy(() -> EventEnvelope.parse(json("{" + id + ok.replace("\"producer\":\"p\"", "\"producer\":\" \"") + "}"),
                objectMapper)).hasMessageContaining("producer");
    }

    private static AppProperties props() {
        return new AppProperties(new AppProperties.Security(null, null, null, "t", List.of()),
                new AppProperties.Events("e", "d", "q", "dq", 10, 3), new AppProperties.Delivery(3, 1, 100, 1),
                new AppProperties.Clients("u", "u", "u", 100, 0), new AppProperties.Mail("SwiftDrop <no-reply@swiftdrop.lk>"),
                new AppProperties.Sms("u", "k", false), "t");
    }

    @Test
    void smtpSenderBuildsAPlainTextMailWithAttachment() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new JavaMailSenderImpl().createMimeMessage());
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, props());

        sender.send(new OutgoingEmail("a@b.lk", "Subject", "Body", "r.pdf", new byte[] {1, 2}));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("Subject");
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("a@b.lk");
        assertThat(sent.getValue().getContent()).isInstanceOf(Multipart.class);
        assertThat(new OutgoingEmail("a@b.lk", "S", "B").toString()).doesNotContain("a@b.lk");
    }

    @Test
    void smtpFailuresAreTemporary() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new JavaMailSenderImpl().createMimeMessage());
        doThrow(new MailSendException("connection refused to a@b.lk")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> new SmtpEmailSender(mailSender, props()).send(new OutgoingEmail("a@b.lk", null, "B")))
                .isInstanceOfSatisfying(DeliveryException.class, e -> {
                    assertThat(e.isTemporary()).isTrue();
                    assertThat(e.getMessage()).doesNotContain("a@b.lk");
                });
    }
}
