package xyz.rawmanoj.mrbank.service;

public interface EmailService {

    /** Emails the one-time code to the address. */
    void sendOtp(String email, String otp);
}
