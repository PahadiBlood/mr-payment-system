package xyz.rawmanoj.mrbank.service;

import xyz.rawmanoj.mrbank.entity.User;

public interface JwtTokenService {
    String generateAccessToken(User user);

    String generateRefreshTokenValue();

    String extractSubject(String token);

    boolean isAccessTokenValid(String token);
}
