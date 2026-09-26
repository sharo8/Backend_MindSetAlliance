package com.mindsetalliance.core.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!test")
@Order(20)
public class MailStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailStartupRunner.class);

    private final EmailService emailService;
    private final String testTo;

    public MailStartupRunner(EmailService emailService, @Value("${MAIL_TEST_TO:}") String testTo) {
        this.emailService = emailService;
        this.testTo = testTo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!emailService.isConfigured()) {
            log.warn("SMTP non configuré (MAIL_SMTP_USERNAME / MAIL_SMTP_PASSWORD). Les e-mails 2FA et de bienvenue ne partiront pas.");
            return;
        }
        log.info("SMTP configuré (identifiants chargés depuis l’environnement, jamais journalisés).");
        if (!StringUtils.hasText(testTo)) {
            return;
        }
        try {
            emailService.envoyerTest(testTo.trim());
            log.info("E-mail de test SMTP envoyé.");
        } catch (Exception ex) {
            log.warn("E-mail de test SMTP non envoyé.");
        }
    }
}
