package lk.swiftdrop.identity.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("support@swiftdrop.lk");
            helper.setTo(toEmail);
            helper.setSubject("SwiftDrop: Reset your password");

            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0b0f14; color: #ffffff; padding: 20px; }
                        .card { background-color: #101513; border: 1px solid rgba(255,255,255,0.1); border-radius: 12px; max-width: 500px; margin: 0 auto; padding: 32px; }
                        .brand { font-size: 24px; font-weight: 800; color: #ffffff; margin-bottom: 24px; }
                        .brand span { color: #f59e0b; }
                        .title { font-size: 20px; font-weight: 700; margin-bottom: 12px; color: #ffffff; }
                        .text { font-size: 14px; color: #94a3b8; line-height: 1.6; margin-bottom: 24px; }
                        .btn { display: inline-block; background-color: #f59e0b; color: #000000; font-weight: 800; font-size: 14px; text-decoration: none; padding: 12px 24px; border-radius: 8px; text-transform: uppercase; }
                        .footer { font-size: 12px; color: #64748b; margin-top: 32px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 16px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="brand">Swift<span>Drop</span></div>
                        <div class="title">Reset Your Password</div>
                        <p class="text">We received a request to reset your password. Click the button below to choose a new password. This recovery link is valid for 15 minutes.</p>
                        <p><a href="%s" class="btn">Reset Password &rarr;</a></p>
                        <p class="text" style="font-size: 12px; margin-top: 20px;">If you did not request a password reset, you can safely ignore this email. Your account remains secure.</p>
                        <div class="footer">&copy; %d SwiftDrop. Island-Wide Courier Operations.</div>
                    </div>
                </body>
                </html>
                """.formatted(resetLink, Year.now().getValue());

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Dispatched password reset email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}

