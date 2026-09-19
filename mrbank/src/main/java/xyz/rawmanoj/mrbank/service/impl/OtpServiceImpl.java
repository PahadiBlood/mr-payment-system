package xyz.rawmanoj.mrbank.service.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.dto.request.SendOtpRequest;
import xyz.rawmanoj.mrbank.exception.ErrorCode;
import xyz.rawmanoj.mrbank.exception.MrBankException;
import xyz.rawmanoj.mrbank.repository.UserRepository;
import xyz.rawmanoj.mrbank.service.EmailService;
import xyz.rawmanoj.mrbank.service.RedisCacheService;

import java.time.Duration;
import java.util.Random;

@AllArgsConstructor
@Service
public class OtpServiceImpl {
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;

    public void sendOtp(SendOtpRequest request) {
        try {
            //verify email
            if (!userRepository.existsByEmail(request.email())) {
                throw new MrBankException(ErrorCode.RESOURCE_NOT_FOUND, "Email does not exist");
            }
            //generate otp
            String otp = generateOtp();
            //store in cache
            redisCacheService.set("otp", otp, Duration.ofMinutes(10));
            //send email
            emailService.sendOtp(request.email(), otp);
        } catch (Exception e) {
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String generateOtp() {
        Random random = new Random();
        int otp = random.nextInt(999999);
        return otp + "";
    }

    public void saveOtp(String email, String otp) {
        throw new UnsupportedOperationException("OTP save logic is not implemented yet");
    }

    public boolean verifyOtp(String email, String otp) {
        throw new UnsupportedOperationException("OTP verification logic is not implemented yet");
    }

}
