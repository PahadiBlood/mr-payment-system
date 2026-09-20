package xyz.rawmanoj.mrbank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.rawmanoj.mrbank.dto.request.*;
import xyz.rawmanoj.mrbank.dto.response.AuthResponse;
import xyz.rawmanoj.mrbank.dto.response.MessageResponse;
import xyz.rawmanoj.mrbank.service.impl.AuthServiceImpl;
import xyz.rawmanoj.mrbank.service.impl.OtpServiceImpl;

@Tag(name = "KYC", description = "Kyc Document APIs")
@RequestMapping("/api/v1/secure/kyc")
@RestController
@AllArgsConstructor
@Slf4j
public class KycController {
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
}
