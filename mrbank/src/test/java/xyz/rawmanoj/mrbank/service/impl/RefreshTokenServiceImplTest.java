package xyz.rawmanoj.mrbank.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.rawmanoj.mrbank.config.JwtProperties;
import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.repository.RefreshTokenRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Test
    void createPersistsAHashRatherThanTheRawToken() {
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User user = new User();
        user.setEmail("user@example.com");

        RefreshToken saved = refreshTokenService.createRefreshToken(user, "raw-token");

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo("raw-token");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        verify(refreshTokenRepository).save(saved);
    }

    @Test
    void revokedTokenEndsEverySessionForThatUser() {
        User user = new User();
        user.setEmail("user@example.com");
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevokedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.verifyRefreshToken("raw-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    void expiredTokenDoesNotRevokeOtherSessions() {
        User user = new User();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.verifyRefreshToken("raw-token"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository, never()).deleteByUser(any());
    }
}
