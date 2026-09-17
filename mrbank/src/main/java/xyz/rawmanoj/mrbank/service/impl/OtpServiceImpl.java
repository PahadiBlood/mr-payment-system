package xyz.rawmanoj.mrbank.service.impl;

import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.service.OtpService;

@Service
public class OtpServiceImpl implements OtpService {
    @Override
    public String generateOtp() {
        throw new UnsupportedOperationException("OTP generation logic is not implemented yet");
    }

    @Override
    public void saveOtp(String email, String otp) {
        throw new UnsupportedOperationException("OTP save logic is not implemented yet");
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        throw new UnsupportedOperationException("OTP verification logic is not implemented yet");
    }
}
