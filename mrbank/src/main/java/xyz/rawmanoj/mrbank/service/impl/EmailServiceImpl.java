package xyz.rawmanoj.mrbank.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.service.EmailService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${application.email.from}")
    private String fromEmail;

    @Value("${application.email.from-name}")
    private String fromName;

    @Async
    @Override
    public void sendOtp(String email, String otp) {
        try {
            // This is for testing purposes only; use a Thymeleaf template to send real emails.

            log.info("Mail sent to email : {}", email);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Your MR Bank OTP");
            message.setText("Your One-Time Password (OTP) for MR Bank is: " + otp + "\n\n"
                    + "This OTP is valid for 10 minutes.\n"
                    + "Do not share this OTP with anyone.\n\n"
                    + "If you didn't request this, please ignore this email.");

            log.info("Sending OTP email to {} with otp {}", email, otp);
            mailSender.send(message);
            log.info("OTP sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP to: {}", email, e);
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}
