package xyz.rawmanoj.mrbank.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    @Override
    public void sendOtp(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Your MR Bank OTP");
            message.setText("Your One-Time Password (OTP) for MR Bank is: " + otp + "\n\n"
                    + "This OTP is valid for 10 minutes.\n"
                    + "Do not share this OTP with anyone.\n\n"
                    + "If you didn't request this, please ignore this email.");

            mailSender.send(message);
            log.info("OTP sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP to: {}", email, e);
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}
