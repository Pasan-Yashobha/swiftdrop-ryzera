package lk.swiftdrop.notification.channel;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lk.swiftdrop.notification.config.AppProperties;
import lk.swiftdrop.notification.notification.DeliveryException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email over SMTP (Spring {@code spring.mail.*}: Mailpit on port 1025 in development, its inbox on
 * http://localhost:8025). Bodies are sent as plain text.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mail().from();
    }

    @Override
    public void send(OutgoingEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, email.hasAttachment(),
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject() == null ? "" : email.subject());
            helper.setText(email.body(), false);
            if (email.hasAttachment()) {
                helper.addAttachment(email.attachmentName(), new ByteArrayResource(email.attachment()),
                        "application/pdf");
            }
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            // SMTP errors can echo the address; only the type is kept.
            throw DeliveryException.temporary("SMTP send failed (" + e.getClass().getSimpleName() + ")");
        }
    }
}
