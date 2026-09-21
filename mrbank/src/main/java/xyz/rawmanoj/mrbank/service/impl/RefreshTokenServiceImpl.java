package xyz.rawmanoj.mrbank.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.rawmanoj.mrbank.config.JwtProperties;
import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public RefreshToken createRefreshToken(User user, String refreshToken) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hashToken(refreshToken));
        token.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpiration()));
        try {
            //throwing custom error
            var i = 1 / 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return refreshTokenRepository.save(token);
    }

    public RefreshToken verifyRefreshToken(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Expired or revoked refresh token");
        }

        return token;
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllUserRefreshTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    public String hashToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
