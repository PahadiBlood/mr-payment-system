package xyz.rawmanoj.mrbank.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.dto.internal.OtpCacheData;
import xyz.rawmanoj.mrbank.dto.request.SendOtpRequest;
import xyz.rawmanoj.mrbank.exception.ErrorCode;
import xyz.rawmanoj.mrbank.exception.MrBankException;
import xyz.rawmanoj.mrbank.repository.UserRepository;
import xyz.rawmanoj.mrbank.service.EmailService;
import xyz.rawmanoj.mrbank.service.OtpService;
import xyz.rawmanoj.mrbank.service.RedisCacheService;
import xyz.rawmanoj.mrbank.util.EmailAddress;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
public class OtpServiceImpl implements OtpService {

    private static final String OTP_CACHE_PREFIX = "otp:";
    private static final String SEND_ATTEMPTS_PREFIX = "otp:send:attempts:";
    private static final String VERIFY_ATTEMPTS_PREFIX = "otp:verify:attempts:";
    private static final String SEND_COOLDOWN_PREFIX = "otp:send:cooldown:";
    private static final String OTP_VERIFICATION_PREFIX = "otp_verified:";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration ATTEMPT_WINDOW = Duration.ofDays(1);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;
    private final int maxAttempts;
    private final byte[] hmacSecret;

    /** Checks the attempt limit and the HMAC secret before the service starts. */
    public OtpServiceImpl(
            EmailService emailService,
            UserRepository userRepository,
            RedisCacheService redisCacheService,
            @Value("${application.otp.max.attempts:5}") int maxAttempts,
            @Value("${application.otp.hmac-secret}") String hmacSecret
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("application.otp.max.attempts must be at least 1");
        }
        byte[] secretBytes = hmacSecret == null ? new byte[0] : hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("application.otp.hmac-secret must be at least 32 bytes");
        }
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.redisCacheService = redisCacheService;
        this.maxAttempts = maxAttempts;
        this.hmacSecret = secretBytes;
    }

    /** Sends a 6-digit code when the email is not already registered, and blocks sends that are too fast or too many. */
    @Override
    public void sendOtp(SendOtpRequest request) {
        String email = request.email();
        boolean reserved = false;
        try {
            email = normalizeEmail(email);
            rejectIfSendQuotaExhausted(email);
            acquireCooldown(email);
            long attempts = redisCacheService.increment(sendAttemptsKey(email), ATTEMPT_WINDOW);
            reserved = true;
            if (attempts > maxAttempts) {
                throw new MrBankException(ErrorCode.TOO_MANY_REQUESTS,
                        "Too many otp requests. Please re-try after 24 hours");
            }
            // Enumeration has to spend the same quota as a real send.
            if (userRepository.existsByEmail(email)) {
                throw new MrBankException(ErrorCode.CONFLICT, "Email already exists");
            }
            deliver(email);
        } catch (MrBankException ex) {
            // A provider or cache failure is ours, so it does not spend the daily quota.
            // The cooldown key stays and blocks a retry storm.
            if (reserved && ex.getErrorCode() == ErrorCode.INTERNAL_SERVER_ERROR) {
                releaseSendAttempt(email);
            }
            if (ex.getErrorCode() != ErrorCode.INTERNAL_SERVER_ERROR) {
                log.warn("OTP send rejected for {}: {}", email, ex.getMessage());
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (reserved) {
                redisCacheService.delete(otpKey(email));
                releaseSendAttempt(email);
            }
            log.error("Unexpected error while sending OTP to {}", email, ex);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** Checks the code and marks the email as verified when the code is right. */
    @Override
    public void verifyOtp(String email, String otp) {
        String normalizedEmail = email;
        try {
            normalizedEmail = normalizeEmail(email);
            String normalizedOtp = normalizeOtp(otp);
            rejectIfVerifyQuotaExhausted(normalizedEmail);

            String cacheKey = otpKey(normalizedEmail);
            OtpCacheData cached = redisCacheService.get(cacheKey, OtpCacheData.class).orElse(null);
            if (cached == null || !normalizedEmail.equals(cached.email()) || !otpMatches(normalizedEmail, cached.otpHash(), normalizedOtp)) {
                registerFailedGuess(normalizedEmail);
                if (cached == null) {
                    throw new MrBankException(ErrorCode.RESOURCE_NOT_FOUND, "OTP does not exist or expired");
                }
                throw new MrBankException(ErrorCode.BAD_REQUEST, "OTP does not match");
            }
            if (userRepository.existsByEmail(normalizedEmail)) {
                redisCacheService.delete(cacheKey);
                throw new MrBankException(ErrorCode.CONFLICT, "Email already exists");
            }
            // Only the caller that still holds this exact challenge may mark the email verified.
            if (!redisCacheService.replaceIfValueEquals(
                    cacheKey,
                    cached,
                    verificationKey(normalizedEmail),
                    "true",
                    OTP_TTL)) {
                throw new MrBankException(ErrorCode.RESOURCE_NOT_FOUND, "OTP does not exist or expired");
            }
            log.info("OTP verified for {}", normalizedEmail);
        } catch (MrBankException ex) {
            log.warn("OTP verification rejected for {}: {}", normalizedEmail, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected error during OTP verification for {}", normalizedEmail, ex);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** Takes the verified flag for this email. The next call returns false until a new code is verified. */
    @Override
    public boolean consumeEmailVerification(String email) {
        String normalizedEmail = email;
        try {
            normalizedEmail = normalizeEmail(email);
            String token = redisCacheService.getAndDelete(verificationKey(normalizedEmail), String.class).orElse(null);
            boolean verified = "true".equalsIgnoreCase(token);
            if (verified) {
                redisCacheService.delete(otpKey(normalizedEmail));
            }
            return verified;
        } catch (MrBankException ex) {
            log.warn("OTP verification consume rejected for {}: {}", normalizedEmail, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected error consuming OTP verification for {}", normalizedEmail, ex);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** Stores the hashed code and emails the plain code. Removes the stored code when the email fails. */
    private void deliver(String email) {
        String otp = generateOtp();
        String cacheKey = otpKey(email);
        redisCacheService.set(cacheKey, new OtpCacheData(email, hmacHex(email, otp)), OTP_TTL);
        try {
            emailService.sendOtp(email, otp);
        } catch (RuntimeException ex) {
            redisCacheService.delete(cacheKey);
            throw new MrBankException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to send OTP email");
        }
        log.info("OTP email accepted for {}", email);
    }

    /** Stops the send when this email has already used its daily limit. */
    private void rejectIfSendQuotaExhausted(String email) {
        long attempts = redisCacheService.getLong(sendAttemptsKey(email)).orElse(0L);
        if (attempts >= maxAttempts) {
            throw new MrBankException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many otp requests. Please re-try after 24 hours");
        }
    }

    /** Holds this email for 30 seconds so two sends cannot run together. */
    private void acquireCooldown(String email) {
        Boolean acquired = redisCacheService.setIfAbsent(cooldownKey(email), Boolean.TRUE, SEND_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new MrBankException(ErrorCode.TOO_MANY_REQUESTS, "Frequent request wait for 30 seconds");
        }
    }

    /** Gives back one daily send when the failure was ours. */
    private void releaseSendAttempt(String email) {
        try {
            redisCacheService.releaseIncrement(sendAttemptsKey(email));
        } catch (RuntimeException ex) {
            log.warn("Could not return an OTP send attempt for {}", email, ex);
        }
    }

    /** Stops verification when this email has used its daily guesses. */
    private void rejectIfVerifyQuotaExhausted(String email) {
        long attempts = redisCacheService.getLong(verifyAttemptsKey(email)).orElse(0L);
        if (attempts >= maxAttempts) {
            throw new MrBankException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many otp verification requests. Please re-try after 24 hours");
        }
    }

    /** Counts a wrong or missing code, and deletes the code after too many guesses. */
    private void registerFailedGuess(String email) {
        long attempts = redisCacheService.increment(verifyAttemptsKey(email), ATTEMPT_WINDOW);
        if (attempts >= maxAttempts) {
            redisCacheService.delete(otpKey(email));
        }
        if (attempts > maxAttempts) {
            throw new MrBankException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many otp verification requests. Please re-try after 24 hours");
        }
    }

    /** Creates a random 6-digit code. */
    private String generateOtp() {
        return "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
    }

    /** Returns true when the given code matches the stored hash for this email. */
    private boolean otpMatches(String email, String expectedHash, String provided) {
        if (expectedHash == null || expectedHash.length() != 64) {
            return false;
        }
        byte[] expected;
        try {
            expected = HEX.parseHex(expectedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, hmac(email, provided));
    }

    /** Turns the email and code into a hex hash. */
    private String hmacHex(String email, String otp) {
        return HEX.formatHex(hmac(email, otp));
    }

    /** Builds the HMAC for this email and code. */
    private byte[] hmac(String email, String otp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            byte[] message = (email + "\n" + otp).getBytes(StandardCharsets.UTF_8);
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("OTP hash is unavailable", ex);
        }
    }

    /** Trims the email, makes it lowercase, and rejects a blank value. */
    private static String normalizeEmail(String email) {
        String normalized = EmailAddress.normalize(email);
        if (normalized == null || normalized.isBlank()) {
            throw new MrBankException(ErrorCode.BAD_REQUEST, "Email is required");
        }
        return normalized;
    }

    /** Accepts only a 6-digit code. */
    private static String normalizeOtp(String otp) {
        if (otp == null || !otp.trim().matches("\\d{6}")) {
            throw new MrBankException(ErrorCode.BAD_REQUEST, "OTP must be a 6-digit code");
        }
        return otp.trim();
    }

    /** Builds the Redis key that stores this email's code. */
    private static String otpKey(String email) {
        return OTP_CACHE_PREFIX + email;
    }

    /** Builds the Redis key that counts sends for this email. */
    private static String sendAttemptsKey(String email) {
        return SEND_ATTEMPTS_PREFIX + email;
    }

    /** Builds the Redis key that counts guesses for this email. */
    private static String verifyAttemptsKey(String email) {
        return VERIFY_ATTEMPTS_PREFIX + email;
    }

    /** Builds the Redis key that holds the 30-second wait. */
    private static String cooldownKey(String email) {
        return SEND_COOLDOWN_PREFIX + email;
    }

    /** Builds the Redis key that remembers a verified email. */
    private static String verificationKey(String email) {
        return OTP_VERIFICATION_PREFIX + email;
    }
}
