package xyz.rawmanoj.mrbank.experiment.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.entity.User;
import xyz.rawmanoj.mrbank.service.impl.RefreshTokenServiceImpl;


/*
 * This is for the testing purpose to how different approaches are used to handle resilience
 * and there drawbacks
 * */
@Slf4j
@Service
public class CustomResilienceHandlerService {
    private final RefreshTokenServiceImpl refreshTokenService;

    public CustomResilienceHandlerService(RefreshTokenServiceImpl refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    //while loop approach
    public void refreshTokenStore(User user, String refreshToken) {
        int attempts = 3;
        int sleepTime = 1000;
        while (attempts > 0) {
            try {
                log.debug("Saving token attempts " + attempts);
                refreshTokenService.createRefreshToken(user, refreshToken);
            } catch (Exception e) {
                log.error("Error while saving refresh token", e);
                try {
                    Thread.sleep(sleepTime);
                    sleepTime *= 3;
                } catch (InterruptedException ex) {

                }

                log.debug("Error while saving attempts remain" + attempts);
            }
            attempts--;
        }
    }
}
