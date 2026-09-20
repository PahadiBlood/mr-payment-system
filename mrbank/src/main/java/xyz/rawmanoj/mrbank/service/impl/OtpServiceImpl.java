package xyz.rawmanoj.mrbank.service.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.dto.request.SendOtpRequest;
import xyz.rawmanoj.mrbank.exception.ErrorCode;
import xyz.rawmanoj.mrbank.exception.MrBankException;
import xyz.rawmanoj.mrbank.repository.UserRepository;
import xyz.rawmanoj.mrbank.service.EmailService;
import xyz.rawmanoj.mrbank.service.RedisCacheService;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class OtpServiceImpl {
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;
    private static final String OTP_CACHE_PREFIX = "otp:";
    private static final String OTP_VERIFICATION_PREFIX = "otp_verified:";
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom secureRandom = new SecureRandom();

    public void sendOtp(SendOtpRequest request) {
        log.debug("Processing OTP send request for email: {}", request.email());
        try {
            if (userRepository.existsByEmail(request.email())) {
                log.warn("Attempted to send OTP to existing email: {}", request.email());
                throw new MrBankException(ErrorCode.CONFLICT, "Email already exist");
            }

            String otp = generateOtp();
            String cacheKey = OTP_CACHE_PREFIX + request.email();
            redisCacheService.set(cacheKey, otp, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
            log.info("OTP generated and cached for email: {}", request.email());

            //need to handle resilience
            emailService.sendOtp(request.email(), otp);
            log.info("OTP sent successfully to email: {}", request.email());
        } catch (MrBankException e) {
            log.error("MrBankException while sending OTP to email: {} - Error: {}", request.email(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while sending OTP to email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String generateOtp() {
        /*
        * Why add 100000?
            random.nextInt(900000) returns a number from 0 to 899999, so it does not always have 6 digits. It could return 42 or 7318, which are 2 and 4 digits.
            Adding 100000 shifts the whole range up:
            Smallest: 0 + 100000 = 100000
            Largest: 899999 + 100000 = 999999
        * */

        int otp = 100000 + secureRandom.nextInt(900000);
        log.debug("Generated OTP with length: {}", String.valueOf(otp).length());
        return String.valueOf(otp);
    }

    public boolean verifyOtp(String email, String otp) {
        log.debug("Verifying OTP for email: {}", email);
        try {
            if (userRepository.existsByEmail(email)) {
                log.warn("OTP verification failed - email already exist: {}", email);
                throw new MrBankException(ErrorCode.CONFLICT, "Email already exist");
            }

            String cacheKey = OTP_CACHE_PREFIX + email;
            Optional<String> cachedOtp = redisCacheService.get(cacheKey, String.class);

            if (cachedOtp.isEmpty()) {
                log.warn("OTP verification failed - OTP expired or not found for email: {}", email);
                throw new MrBankException(ErrorCode.RESOURCE_NOT_FOUND, "OTP does not exist or expired");
            }

            if (!cachedOtp.get().equals(otp)) {
                log.warn("OTP verification failed - OTP mismatch for email: {}", email);
                throw new MrBankException(ErrorCode.RESOURCE_NOT_FOUND, "OTP does not match");
            }

            String verificationKey = OTP_VERIFICATION_PREFIX + email;
            redisCacheService.set(verificationKey, "true", Duration.ofMinutes(OTP_EXPIRY_MINUTES));
            log.info("OTP verified successfully for email: {}", email);
            return true;
        } catch (MrBankException e) {
            log.error("OTP verification error for email: {} - {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OTP verification for email: {} - {}", email, e.getMessage(), e);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public boolean isOtpVerified(String email) {
        log.debug("Checking OTP verification status for email: {}", email);
        try {
            String verificationKey = OTP_VERIFICATION_PREFIX + email;
            Optional<String> isOtpVerified = redisCacheService.get(verificationKey, String.class);

            if (isOtpVerified.isEmpty() || !isOtpVerified.get().equalsIgnoreCase("true")) {
                log.warn("OTP not verified for email: {}", email);
                return false;
            }
            log.debug("OTP verified status confirmed for email: {}", email);
            return true;
        } catch (MrBankException e) {
            log.error("OTP verification check failed for email: {} - {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error checking OTP verification for email: {} - {}", email, e.getMessage(), e);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

}
