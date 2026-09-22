package xyz.rawmanoj.mrbank.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import xyz.rawmanoj.mrbank.dto.request.LoginRequest;
import xyz.rawmanoj.mrbank.dto.request.TokenRefreshRequest;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.repository.UserRepository;
import xyz.rawmanoj.mrbank.service.JwtTokenService;
import xyz.rawmanoj.mrbank.service.OtpService;
import xyz.rawmanoj.mrbank.service.RefreshTokenService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void unknownEmailStillChecksAPasswordHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("secret"), eq(AuthServiceImpl.DUMMY_PASSWORD_HASH))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "secret")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verify(refreshTokenService, never()).createRefreshToken(any(), any());
    }

    @Test
    void dummyHashIsAcceptedByBcrypt() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        assertThat(encoder.matches("password", AuthServiceImpl.DUMMY_PASSWORD_HASH)).isTrue();
    }

    @Test
    void blockedUserDoesNotReceiveTokens() {
        User user = user(UserStatus.BLOCKED);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "secret")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is not active");

        verify(refreshTokenService, never()).createRefreshToken(any(), any());
    }

    @Test
    void activeUserStoresTheRefreshTokenBeforeItIsReturned() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(user)).thenReturn("access");
        when(jwtTokenService.generateRefreshTokenValue()).thenReturn("refresh");

        AuthResponse response = authService.login(new LoginRequest(EMAIL, "secret"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(refreshTokenService).createRefreshToken(user, "refresh");
    }

    @Test
    void refreshRotatesTheRefreshToken() {
        User user = user(UserStatus.ACTIVE);
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        when(refreshTokenService.verifyRefreshToken("old")).thenReturn(existing);
        when(jwtTokenService.generateAccessToken(user)).thenReturn("access");
        when(jwtTokenService.generateRefreshTokenValue()).thenReturn("new-refresh");

        AuthResponse response = authService.refreshToken(new TokenRefreshRequest("old"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        InOrder order = inOrder(refreshTokenService);
        order.verify(refreshTokenService).verifyRefreshToken("old");
        order.verify(refreshTokenService).revokeRefreshToken("old");
        order.verify(refreshTokenService).createRefreshToken(user, "new-refresh");
    }

    @Test
    void inactiveUserCannotRefresh() {
        User user = user(UserStatus.SUSPENDED);
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        when(refreshTokenService.verifyRefreshToken("old")).thenReturn(existing);

        assertThatThrownBy(() -> authService.refreshToken(new TokenRefreshRequest("old")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenService).revokeRefreshToken("old");
        verify(refreshTokenService, never()).createRefreshToken(any(), any());
    }

    private static User user(UserStatus status) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPassword("stored-hash");
        user.setUserStatus(status);
        return user;
    }
}
