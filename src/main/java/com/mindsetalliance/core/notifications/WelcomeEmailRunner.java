package com.mindsetalliance.core.notifications;

import com.mindsetalliance.core.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Order(40)
@ConditionalOnProperty(name = "ma.mail.welcome-on-startup", havingValue = "true", matchIfMissing = true)
public class WelcomeEmailRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WelcomeEmailRunner.class);

    private final AuthService authService;

    public WelcomeEmailRunner(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean sent = authService.envoyerBienvenueSiNecessaire("alinekabacele@gmail.com");
            if (sent) {
                log.info("E-mail de bienvenue administrateur traité.");
            }
        } catch (Exception ex) {
            log.warn("E-mail de bienvenue administrateur non envoyé.");
        }
    }
}
