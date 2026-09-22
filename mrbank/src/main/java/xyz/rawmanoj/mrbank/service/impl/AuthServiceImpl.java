package xyz.rawmanoj.mrbank.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.rawmanoj.mrbank.dto.request.LoginRequest;
import xyz.rawmanoj.mrbank.dto.request.LogoutRequest;
import xyz.rawmanoj.mrbank.dto.request.RegisterRequest;
import xyz.rawmanoj.mrbank.dto.request.TokenRefreshRequest;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.dto.response.MessageResponse;
import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;
import xyz.rawmanoj.mrbank.exception.ConflictException;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.repository.UserRepository;
import xyz.rawmanoj.mrbank.service.AuthService;
import xyz.rawmanoj.mrbank.service.JwtTokenService;
import xyz.rawmanoj.mrbank.service.OtpService;
import xyz.rawmanoj.mrbank.service.RefreshTokenService;
import xyz.rawmanoj.mrbank.util.EmailAddress;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    // Real bcrypt hash so a missing account still pays the compare cost.
    static final String DUMMY_PASSWORD_HASH =
            "$2a$10$v/bt.lixAf.saq/8XBxpS.pxhGJlPSLkfxqC0pvMtTRB/AKXA7KyO";

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    /** Creates the user after the email has been verified with a code. */
    @Override
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = EmailAddress.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected for {}: email already registered", email);
            throw new ConflictException("Email already registered");
        }
        if (!otpService.consumeEmailVerification(email)) {
            log.warn("Registration rejected for {}: email is not OTP verified", email);
            throw new UnauthorizedException("Email must be verified with OTP before registration");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Registration rejected for {}: email was registered concurrently", email);
            throw new ConflictException("Email already registered. Please try logging in or use a different email.");
        }
        log.info("User registered: {}", email);
        return new MessageResponse("User registered successfully");
    }

    /** Checks the password and returns tokens when the account is active. */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = EmailAddress.normalize(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        String passwordHash = user == null || user.getPassword() == null
                ? DUMMY_PASSWORD_HASH
                : user.getPassword();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            log.warn("Login rejected for {}", email);
            throw new UnauthorizedException("Invalid email or password");
        }
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            log.warn("Login rejected for {}: account status {}", email, user.getUserStatus());
            throw new UnauthorizedException("Account is not active");
        }
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshTokenValue();
        refreshTokenService.createRefreshToken(user, refreshToken);
        return new AuthResponse(accessToken, refreshToken);
    }

    /** Checks the refresh token, retires it, and returns a new pair. */
    @Override
    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        try {
            RefreshToken existing = refreshTokenService.verifyRefreshToken(request.refreshToken());
            User user = existing.getUser();
            if (user.getUserStatus() != UserStatus.ACTIVE) {
                refreshTokenService.revokeRefreshToken(request.refreshToken());
                throw new UnauthorizedException("Invalid refresh token");
            }
            refreshTokenService.revokeRefreshToken(request.refreshToken());
            String refreshToken = jwtTokenService.generateRefreshTokenValue();
            refreshTokenService.createRefreshToken(user, refreshToken);
            String accessToken = jwtTokenService.generateAccessToken(user);
            return new AuthResponse(accessToken, refreshToken);
        } catch (UnauthorizedException ex) {
            log.warn("Token refresh rejected: {}", ex.getMessage());
            throw ex;
        }
    }

    /** Retires the refresh token so it cannot be used again. */
    @Override
    public MessageResponse logout(LogoutRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
        return new MessageResponse("Logged out successfully");
    }
}
