package xyz.rawmanoj.mrbank.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.rawmanoj.mrbank.config.JwtProperties;
import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.repository.RefreshTokenRepository;
import xyz.rawmanoj.mrbank.service.RefreshTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    /** Saves a hash of the refresh token for this user. */
    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user, String refreshToken) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hashToken(refreshToken));
        token.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpiration()));
        return refreshTokenRepository.save(token);
    }

    /** Loads an active refresh token. A retired token ends every session for that user. */
    @Override
    @Transactional
    public RefreshToken verifyRefreshToken(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        User user = token.getUser();
        if (user == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        // Touch the account while this row lock is held so the caller does not lazy-load later.
        user.getEmail();
        // A revoked token presented again means the rotated token leaked, so end every session.
        if (token.getRevokedAt() != null) {
            refreshTokenRepository.deleteByUser(user);
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Expired or revoked refresh token");
        }

        return token;
    }

    /** Marks this refresh token as retired. */
    @Override
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    /** Deletes every refresh token for this user. */
    @Override
    @Transactional
    public void revokeAllUserRefreshTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    /** Turns the refresh token into a hash that is safe to store. */
    private String hashToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
