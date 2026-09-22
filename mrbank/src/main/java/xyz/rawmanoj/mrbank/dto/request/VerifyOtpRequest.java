package xyz.rawmanoj.mrbank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import xyz.rawmanoj.mrbank.util.EmailAddress;

public record VerifyOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must be a 6-digit code") String otp
) {
    public VerifyOtpRequest {
        email = EmailAddress.normalize(email);
        otp = otp == null ? null : otp.trim();
    }
}
