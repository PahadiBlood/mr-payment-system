package xyz.rawmanoj.mrbank.dto.request;

public record VerifyOtpRequest(
        String email,
        String otp
) {
}
