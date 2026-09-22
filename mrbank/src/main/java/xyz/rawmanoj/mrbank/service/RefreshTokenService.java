package xyz.rawmanoj.mrbank.service;

import xyz.rawmanoj.mrbank.entity.RefreshToken;
import xyz.rawmanoj.mrbank.entity.User;

public interface RefreshTokenService {

    /** Saves a hash of the refresh token for this user. */
    RefreshToken createRefreshToken(User user, String refreshToken);

    /** Loads an active refresh token. A retired token ends every session for that user. */
    RefreshToken verifyRefreshToken(String refreshToken);

    /** Marks this refresh token as retired. */
    void revokeRefreshToken(String refreshToken);

    /** Deletes every refresh token for this user. */
    void revokeAllUserRefreshTokens(User user);
}
