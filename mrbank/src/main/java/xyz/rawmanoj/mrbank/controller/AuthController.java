package xyz.rawmanoj.mrbank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.rawmanoj.mrbank.dto.request.LoginRequest;
import xyz.rawmanoj.mrbank.dto.request.LogoutRequest;
import xyz.rawmanoj.mrbank.dto.request.RegisterRequest;
import xyz.rawmanoj.mrbank.dto.request.SendOtpRequest;
import xyz.rawmanoj.mrbank.dto.request.TokenRefreshRequest;
import xyz.rawmanoj.mrbank.dto.request.VerifyOtpRequest;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.dto.response.MessageResponse;
import xyz.rawmanoj.mrbank.service.impl.AuthServiceImpl;
import xyz.rawmanoj.mrbank.service.impl.OtpServiceImpl;
import xyz.rawmanoj.mrbank.util.CommonUtils;

@Tag(name = "Authentication", description = "Public authentication APIs")
@RequestMapping("/api/v1/public/auth")
@RestController
@AllArgsConstructor
@Slf4j
public class AuthController {
    private final AuthServiceImpl authService;
    private final OtpServiceImpl otpService;

    @Operation(summary = "Register user", description = "Creates a new user account. Business logic is implemented in the auth service.")
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received for email: {}", request.email());
        try {
            MessageResponse response = authService.register(request);
            log.info("User registered successfully: {}", request.email());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Registration failed for email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Send OTP", description = "Generates and stores an OTP for the provided email.")
    @PostMapping("/send-otp")
    public ResponseEntity<MessageResponse> sendOtp(@Valid @RequestBody SendOtpRequest request, HttpServletRequest req) {
        log.info("Send OTP request received for email: {}", request.email());
        try {
            otpService.sendOtp(request, CommonUtils.getClientIp(req));
            log.info("OTP sent successfully to email: {}", request.email());
            return ResponseEntity.ok(new MessageResponse("OTP sent successfully"));
        } catch (Exception e) {
            log.error("Failed to send OTP to email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Verify OTP", description = "Verifies the OTP submitted for the provided email.")
    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        log.info("Verify OTP request received for email: {}", request.email());
        try {
            boolean verified = otpService.verifyOtp(request.email(), request.otp());
            if (verified) {
                log.info("OTP verified successfully for email: {}", request.email());
                return ResponseEntity.ok(new MessageResponse("OTP verified successfully"));
            } else {
                log.warn("Invalid OTP attempt for email: {}", request.email());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid OTP"));
            }
        } catch (Exception e) {
            log.error("OTP verification failed for email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Login user", description = "Authenticates a user and returns access and refresh tokens.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for email: {}", request.email());
        try {
            AuthResponse response = authService.login(request);
            log.info("User login successful: {}", request.email());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Login failed for email: {} - Error: {}", request.email(), e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Refresh access token", description = "Verifies a refresh token and returns a new access token.")
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        log.info("Token refresh request received");
        try {
            AuthResponse response = authService.refreshToken(request);
            log.info("Access token refreshed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Token refresh failed - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Logout user", description = "Revokes the provided refresh token.")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Logout request received");
        try {
            MessageResponse response = authService.logout(request);
            log.info("User logged out successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Logout failed - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
