package xyz.rawmanoj.mrbank.service;

import xyz.rawmanoj.mrbank.entity.User;

public interface JwtTokenService {

    /** Builds a signed access token for this user. */
    String generateAccessToken(User user);

    /** Builds a random refresh token. */
    String generateRefreshTokenValue();

    /** Reads the email stored inside a signed access token. */
    String extractSubject(String token);

    /** Returns true when the access token signature and expiry are valid. */
    boolean isAccessTokenValid(String token);
}
