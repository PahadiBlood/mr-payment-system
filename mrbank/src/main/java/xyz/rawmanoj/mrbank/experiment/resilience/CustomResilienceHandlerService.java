package xyz.rawmanoj.mrbank.experiment.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.service.impl.RefreshTokenServiceImpl;

@Slf4j
@Service
public class CustomResilienceHandlerService {
    private final RefreshTokenServiceImpl refreshTokenService;

    public CustomResilienceHandlerService(RefreshTokenServiceImpl refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    public void refreshTokenStore(User user, String refreshToken) {
        int maxAttempts = 3;
        int sleepTime = 1000;
        while (maxAttempts > 0) {
            try {
                refreshTokenService.createRefreshToken(user, refreshToken);
            } catch (Exception e) {
                log.error("Error while saving refresh token", e);
                try {
                    Thread.sleep(sleepTime);
                    sleepTime *= 3;
                } catch (InterruptedException ex) {

                }
            }
            maxAttempts--;
        }
    }
}
