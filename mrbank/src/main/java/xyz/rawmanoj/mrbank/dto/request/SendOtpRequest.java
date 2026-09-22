package xyz.rawmanoj.mrbank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import xyz.rawmanoj.mrbank.util.EmailAddress;

public record SendOtpRequest(
        @NotBlank @Email String email
) {
    public SendOtpRequest {
        email = EmailAddress.normalize(email);
    }
}
