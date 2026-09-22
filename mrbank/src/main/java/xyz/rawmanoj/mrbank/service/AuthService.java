package xyz.rawmanoj.mrbank.service;

import xyz.rawmanoj.mrbank.dto.request.LoginRequest;
import xyz.rawmanoj.mrbank.dto.request.LogoutRequest;
import xyz.rawmanoj.mrbank.dto.request.RegisterRequest;
import xyz.rawmanoj.mrbank.dto.request.TokenRefreshRequest;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.dto.response.MessageResponse;

public interface AuthService {

    /** Creates the user after the email has been verified with a code. */
    MessageResponse register(RegisterRequest request);

    /** Checks the password and returns tokens when the account is active. */
    AuthResponse login(LoginRequest request);

    /** Checks the refresh token, retires it, and returns a new pair. */
    AuthResponse refreshToken(TokenRefreshRequest request);

    /** Retires the refresh token so it cannot be used again. */
    MessageResponse logout(LogoutRequest request);
}
