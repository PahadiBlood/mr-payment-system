package xyz.rawmanoj.mrbank.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.service.EmailService;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;

    /** Rejects a blank sender address or sender name. */
    public EmailServiceImpl(
            JavaMailSender mailSender,
            @Value("${application.email.from}") String fromEmail,
            @Value("${application.email.from-name}") String fromName
    ) {
        if (fromEmail == null || fromEmail.isBlank() || fromName == null || fromName.isBlank()) {
            throw new IllegalArgumentException("application.email.from and application.email.from-name must be set");
        }
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    /** Emails the one-time code to the address. */
    @Override
    public void sendOtp(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail, fromName);
            helper.setTo(email);
            helper.setSubject("Your MR Bank OTP");
            helper.setText("""
                    Your One-Time Password (OTP) for MR Bank is: %s

                    This OTP is valid for 10 minutes.
                    Do not share this OTP with anyone.

                    If you didn't request this, please ignore this email.
                    """.formatted(otp));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            // Some provider exceptions echo the message body. Keep the code out of the log.
            log.error("Failed to send OTP email to {}: {}", email, safeFailureDetail(ex, otp));
            throw new IllegalStateException("Failed to send OTP email", ex);
        }
    }

    /** Builds a log line that does not include the code. */
    private static String safeFailureDetail(Throwable failure, String otp) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && otp != null && message.contains(otp)) {
                return failure.getClass().getSimpleName();
            }
            Throwable next = current.getCause();
            current = next == current ? null : next;
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return failure.getClass().getSimpleName() + ": " + message;
    }
}
