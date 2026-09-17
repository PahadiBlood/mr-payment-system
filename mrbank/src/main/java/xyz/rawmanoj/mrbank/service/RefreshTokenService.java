package xyz.rawmanoj.mrbank.service;

import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(User user, String refreshToken);

    RefreshToken verifyRefreshToken(String refreshToken);

    void revokeRefreshToken(String refreshToken);

    void revokeAllUserRefreshTokens(User user);

    String hashToken(String refreshToken);
}
