package xyz.rawmanoj.mrbank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.rawmanoj.mrbank.dto.request.LoginRequest;
import xyz.rawmanoj.mrbank.dto.request.LogoutRequest;
import xyz.rawmanoj.mrbank.dto.request.RegisterRequest;
import xyz.rawmanoj.mrbank.dto.request.TokenRefreshRequest;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.dto.response.MessageResponse;
import xyz.rawmanoj.mrbank.service.impl.AuthServiceImpl;

@Tag(name = "Authentication", description = "Public authentication APIs")
@RequestMapping("/api/v1/public/auth")
@RestController
@AllArgsConstructor
public class AuthController {
    private final AuthServiceImpl authService;

    @Operation(summary = "Register user", description = "Creates a new user account. Business logic is implemented in the auth service.")
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Login user", description = "Authenticates a user and returns access and refresh tokens.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Refresh access token", description = "Verifies a refresh token and returns a new access token.")
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @Operation(summary = "Logout user", description = "Revokes the provided refresh token.")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestBody LogoutRequest request) {
        return ResponseEntity.ok(authService.logout(request));
    }
}
