package xyz.rawmanoj.mrbank.dto.internal;

import java.time.Instant;

public record OtpCacheData(
        String email,
        String otp,
        Instant createdAt
) {
}
