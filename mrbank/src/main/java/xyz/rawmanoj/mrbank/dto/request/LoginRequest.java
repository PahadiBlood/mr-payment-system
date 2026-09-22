package xyz.rawmanoj.mrbank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import xyz.rawmanoj.mrbank.util.EmailAddress;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
    public LoginRequest {
        email = EmailAddress.normalize(email);
    }
}
