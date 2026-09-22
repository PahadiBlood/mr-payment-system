package xyz.rawmanoj.mrbank.dto.internal;

/**
 * Cached OTP challenge. {@code otpHash} is an HMAC of the code, never the code itself.
 */
public record OtpCacheData(
        String email,
        String otpHash
) {
}
