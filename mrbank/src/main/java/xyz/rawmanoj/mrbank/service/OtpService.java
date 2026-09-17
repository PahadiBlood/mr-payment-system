package xyz.rawmanoj.mrbank.service;

public interface OtpService {
    String generateOtp();

    void saveOtp(String email, String otp);

    boolean verifyOtp(String email, String otp);
}
