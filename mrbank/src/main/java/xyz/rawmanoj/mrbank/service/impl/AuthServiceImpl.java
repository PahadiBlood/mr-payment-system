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
import xyz.rawmanoj.mrbank.exception.ConflictException;
import xyz.rawmanoj.mrbank.exception.UnauthorizedException;
import xyz.rawmanoj.mrbank.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl {
    private final JwtTokenServiceImpl jwtTokenService;
    private final RefreshTokenServiceImpl refreshTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpServiceImpl otpService;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = request.email();
        log.debug("Processing registration for email: {}", email);

        try {
            // Step 1: Verify OTP was validated for this email
            log.debug("Verifying OTP status for email: {}", email);
            if (!otpService.isOtpVerified(email)) {
                log.warn("Registration attempt with unverified OTP for email: {}", email);
                throw new UnauthorizedException("Email must be verified with OTP before registration");
            }

            // Step 2: Check if email already exists (race condition check)
            log.debug("Checking if email already exists in database: {}", email);
            if (userRepository.existsByEmail(email)) {
                log.warn("Registration attempt with existing email: {}", email);
                throw new ConflictException("Email already registered");
            }

            // Step 3: Create and save new user
            log.debug("Creating new user entity for email: {}", email);
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(request.password()));

            try {
                User savedUser = userRepository.save(user);
                log.info("User registered successfully with email: {}", email);
                return new MessageResponse("User registered successfully");
            } catch (DataIntegrityViolationException e) {
                // Handle race condition: another thread inserted the same email concurrently
                log.warn("Race condition detected: Email was registered concurrently for email: {} - {}", 
                         email, e.getMessage());
                throw new ConflictException("Email already registered. Please try logging in or use a different email.");
            }

        } catch (UnauthorizedException | ConflictException e) {
            log.error("Registration validation failed for email: {} - {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during registration for email: {} - {}", email, e.getMessage(), e);
            throw new RuntimeException("Registration failed. Please try again later.");
        }
    }

    public AuthResponse login(LoginRequest request) {
        log.debug("Processing login for email: {}", request.email());
        throw new UnsupportedOperationException("Login logic is not implemented yet");
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        log.debug("Processing token refresh");
        try {
            RefreshToken refreshToken = refreshTokenService.verifyRefreshToken(request.refreshToken());
            User user = refreshToken.getUser();

            if (user == null) {
                log.warn("Invalid refresh token: user is null");
                throw new UnauthorizedException("Invalid refresh token");
            }

            String accessToken = jwtTokenService.generateAccessToken(user);
            log.debug("Access token refreshed successfully for user: {}", user.getEmail());
            return new AuthResponse(accessToken, request.refreshToken());
        } catch (UnauthorizedException e) {
            log.error("Unauthorized token refresh attempt: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error during token refresh: {}", e.getMessage(), e);
            throw new UnauthorizedException("Failed to refresh token");
        }
    }

    public MessageResponse logout(LogoutRequest request) {
        log.debug("Processing logout");
        try {
            refreshTokenService.revokeRefreshToken(request.refreshToken());
            log.info("User logged out successfully");
            return new MessageResponse("Logged out successfully");
        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage(), e);
            throw e;
        }
    }
}
