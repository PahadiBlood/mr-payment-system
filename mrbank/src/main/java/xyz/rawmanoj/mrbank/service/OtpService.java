package xyz.rawmanoj.mrbank.service;

import xyz.rawmanoj.mrbank.dto.request.SendOtpRequest;

public interface OtpService {

    /** Sends a 6-digit code when the email is not already registered. */
    void sendOtp(SendOtpRequest request);

    /** Checks the code and marks the email as verified when the code is right. */
    void verifyOtp(String email, String otp);

    /** Takes the verified flag for this email. The next call returns false until a new code is verified. */
    boolean consumeEmailVerification(String email);
}
