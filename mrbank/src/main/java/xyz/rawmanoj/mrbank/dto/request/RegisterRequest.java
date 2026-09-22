package xyz.rawmanoj.mrbank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import xyz.rawmanoj.mrbank.util.EmailAddress;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
    public RegisterRequest {
        email = EmailAddress.normalize(email);
    }
}
