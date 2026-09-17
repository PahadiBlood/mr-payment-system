package xyz.rawmanoj.mrbank.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.dto.request.LoginRequest;
import xyz.rawmanoj.mrbank.dto.request.LogoutRequest;
import xyz.rawmanoj.mrbank.dto.request.RegisterRequest;
import xyz.rawmanoj.mrbank.dto.request.TokenRefreshRequest;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.dto.response.MessageResponse;
import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.service.AuthService;
import xyz.rawmanoj.mrbank.service.JwtTokenService;
import xyz.rawmanoj.mrbank.service.RefreshTokenService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public MessageResponse register(RegisterRequest request) {
        throw new UnsupportedOperationException("Register logic is not implemented yet");
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("Login logic is not implemented yet");
    }

    @Override
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.verifyRefreshToken(request.refreshToken());
        User user = refreshToken.getUser();

        if (user == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        String accessToken = jwtTokenService.generateAccessToken(user);
        return new AuthResponse(accessToken, request.refreshToken());
    }

    @Override
    public MessageResponse logout(LogoutRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
        return new MessageResponse("Logged out successfully");
    }
}
