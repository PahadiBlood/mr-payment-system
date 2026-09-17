package xyz.rawmanoj.mrbank.service.impl;

import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.service.EmailService;

@Service
public class EmailServiceImpl implements EmailService {
    @Override
    public void sendOtp(String email, String otp) {
        throw new UnsupportedOperationException("Email sending logic is not implemented yet");
    }
}
