package com.mindsetalliance.core.notifications;

import com.mindsetalliance.core.common.BusinessException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Envoi SMTP via JavaMailSender.
 * À terme : migrer vers un service transactionnel dédié (SendGrid, Mailgun ou Amazon SES)
 * pour la délivrabilité et la séparation usage personnel / usage applicatif.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final String smtpUsername;
    private final String smtpPassword;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${ma.mail.from-address:}") String fromAddress,
            @Value("${ma.mail.from-name:Mindset Alliance — MA Workspace}") String fromName,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(smtpUsername) && StringUtils.hasText(smtpPassword);
    }

    public void envoyerCode2FA(String destinataire, String code) {
        String plain = "Votre code de vérification MA Workspace est " + code + ". Il expire dans 5 minutes.";
        String html = layout(
                "Code de vérification",
                "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:15px;line-height:1.6;\">Saisissez ce code dans MA Workspace. Il expire dans <strong style=\"color:#f6d06a;\">5 minutes</strong>.</p>"
                        + badge(code)
                        + "<p style=\"margin:20px 0 0;color:#9aa6c2;font-size:13px;line-height:1.55;\">Si vous n’êtes pas à l’origine de cette connexion, ignorez ce message.</p>");
        envoyer(destinataire, "Votre code de vérification MA Workspace", plain, html);
    }

    public void envoyerBienvenue(String destinataire, String nomComplet, String lienDefinitionMotDePasse) {
        envoyerBienvenue(destinataire, nomComplet, lienDefinitionMotDePasse, null, null);
    }

    public void envoyerBienvenue(String destinataire, String nomComplet, String lienDefinitionMotDePasse,
                                 String departement, String roles) {
        String dept = departement == null || departement.isBlank() ? "—" : departement;
        String roleTxt = roles == null || roles.isBlank() ? "—" : roles;
        String plain = "Bienvenue " + nomComplet + ". Département : " + dept + ". Rôles : " + roleTxt
                + ". Définissez votre mot de passe (24 h) : " + lienDefinitionMotDePasse;
        String html = layout(
                "Bienvenue sur MA Workspace",
                "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:15px;line-height:1.6;\">Bonjour " + escape(nomComplet)
                        + ", votre compte MA Workspace est prêt.</p>"
                        + recapRow("Département", dept)
                        + recapRow("Rôle(s)", roleTxt)
                        + cta("Définir mon mot de passe", lienDefinitionMotDePasse)
                        + "<p style=\"margin:20px 0 0;color:#9aa6c2;font-size:13px;line-height:1.55;\">Ce lien est valable 24 heures. Un code de vérification vous sera ensuite envoyé par e-mail.</p>");
        envoyer(destinataire, "Bienvenue sur MA Workspace", plain, html);
    }

    @Async
    public void envoyerChangementAccesAsync(String destinataire, String nomComplet, String avant, String apres,
                                            String date, String auteur) {
        envoyerSansBloquer(() -> {
            String plain = "Bonjour " + nomComplet + ". Vos accès MA Workspace ont été mis à jour le " + date
                    + " par " + auteur + ".\n\nAvant :\n" + avant + "\n\nAprès :\n" + apres;
            log.info("E-mail changement d'acces (texte) : {}", plain.replace("\n", " | "));
            String html = layout(
                    "Mise à jour de vos accès",
                    "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:15px;line-height:1.6;\">Bonjour " + escape(nomComplet)
                            + ", un administrateur a modifié vos accès le <strong style=\"color:#f6d06a;\">" + escape(date)
                            + "</strong>.</p>"
                            + recapRow("Modifié par", auteur)
                            + "<p style=\"margin:18px 0 8px;letter-spacing:0.16em;font-size:11px;color:#f6d06a;\">AVANT</p>"
                            + "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:14px;line-height:1.6;white-space:pre-line;\">" + escape(avant) + "</p>"
                            + "<p style=\"margin:18px 0 8px;letter-spacing:0.16em;font-size:11px;color:#f6d06a;\">APRÈS</p>"
                            + "<p style=\"margin:0 0 8px;color:#d5dcec;font-size:14px;line-height:1.6;white-space:pre-line;\">" + escape(apres) + "</p>"
                            + "<p style=\"margin:20px 0 0;color:#9aa6c2;font-size:13px;line-height:1.55;\">Si cette modification vous surprend, contactez votre administrateur.</p>");
            envoyer(destinataire, "Vos accès MA Workspace ont été mis à jour", plain, html);
        });
    }

    @Async
    public void envoyerMatricePermissionsAsync(String destinataire, String nomComplet, String rolesPhrase,
                                               String details, String date, String auteur) {
        envoyerSansBloquer(() -> {
            String plain = "Bonjour " + nomComplet + ". Les permissions associées à " + rolesPhrase
                    + " ont été mises à jour le " + date + " par " + auteur + ".\n\n" + details;
            log.info("E-mail matrice rôles (texte) : {}", plain.replace("\n", " | "));
            String html = layout(
                    "Permissions de votre rôle",
                    "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:15px;line-height:1.6;\">Bonjour "
                            + escape(nomComplet)
                            + ", les permissions associées à <strong style=\"color:#f6d06a;\">" + escape(rolesPhrase)
                            + "</strong> ont été mises à jour le <strong style=\"color:#f6d06a;\">" + escape(date)
                            + "</strong> par " + escape(auteur) + ".</p>"
                            + recapRow("Modifié par", auteur)
                            + "<p style=\"margin:18px 0 8px;letter-spacing:0.16em;font-size:11px;color:#f6d06a;\">DÉTAIL</p>"
                            + "<p style=\"margin:0 0 8px;color:#d5dcec;font-size:14px;line-height:1.6;white-space:pre-line;\">"
                            + escape(details) + "</p>"
                            + "<p style=\"margin:20px 0 0;color:#9aa6c2;font-size:13px;line-height:1.55;\">Si cette modification vous surprend, contactez votre administrateur.</p>");
            envoyer(destinataire, "Les permissions de votre rôle ont été mises à jour", plain, html);
        });
    }

    @Async
    public void envoyerDesactivationAsync(String destinataire, String nomComplet, String date) {
        envoyerSansBloquer(() -> {
            String plain = "Bonjour " + nomComplet + ". Votre compte MA Workspace a été désactivé le " + date + ".";
            log.info("E-mail désactivation (texte) : {}", plain);
            String html = layout(
                    "Compte désactivé",
                    "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:15px;line-height:1.6;\">Bonjour " + escape(nomComplet)
                            + ", votre accès à MA Workspace a été désactivé le <strong style=\"color:#f6d06a;\">"
                            + escape(date) + "</strong>.</p>"
                            + "<p style=\"margin:0;color:#9aa6c2;font-size:13px;line-height:1.55;\">Pour toute question, contactez votre administrateur. Aucun mot de passe n’est inclus dans ce message.</p>");
            envoyer(destinataire, "Votre compte MA Workspace a été désactivé", plain, html);
        });
    }

    private void envoyerSansBloquer(Runnable envoi) {
        if (!isConfigured()) {
            log.warn("E-mail administratif non envoyé : SMTP non configuré.");
            return;
        }
        try {
            envoi.run();
        } catch (Exception ex) {
            log.warn("Échec d’envoi e-mail administratif (action conservée) : {}", sanitizeSmtpError(ex));
        }
    }

    private static String recapRow(String label, String value) {
        return "<p style=\"margin:0 0 10px;color:#d5dcec;font-size:14px;line-height:1.55;\"><span style=\"color:#f6d06a;letter-spacing:0.12em;font-size:11px;\">"
                + escape(label).toUpperCase() + "</span><br>" + escape(value) + "</p>";
    }

    public void envoyerTest(String destinataire) {
        envoyer(
                destinataire,
                "Test SMTP MA Workspace",
                "Ceci est un e-mail de test. La configuration SMTP de MA Workspace fonctionne.",
                layout("Test SMTP", "<p style=\"margin:0;color:#d5dcec;font-size:15px;\">La configuration SMTP de MA Workspace fonctionne.</p>"));
    }

    public void envoyerReinitialisationMotDePasse(String destinataire, String nomComplet, String lien) {
        String plain = "Réinitialisation MA Workspace pour " + nomComplet + ". Lien (24 h) : " + lien;
        String html = layout(
                "Réinitialiser le mot de passe",
                "<p style=\"margin:0 0 16px;color:#d5dcec;font-size:15px;line-height:1.6;\">Bonjour " + escape(nomComplet)
                        + ", une demande de réinitialisation a été faite pour votre compte.</p>"
                        + cta("Choisir un nouveau mot de passe", lien)
                        + "<p style=\"margin:20px 0 0;color:#9aa6c2;font-size:13px;line-height:1.55;\">Lien valable 24 heures. Si vous n’êtes pas à l’origine de cette demande, ignorez cet e-mail : votre mot de passe actuel reste inchangé.</p>");
        envoyer(destinataire, "Réinitialisation de votre mot de passe MA Workspace", plain, html);
    }

    private void envoyer(String destinataire, String objet, String texte, String html) {
        if (!isConfigured()) {
            throw new BusinessException("Envoi d’e-mail indisponible : SMTP non configuré.", 503);
        }
        if (!StringUtils.hasText(destinataire)) {
            throw new BusinessException("Destinataire d’e-mail manquant", 400);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = StringUtils.hasText(fromAddress) ? fromAddress : smtpUsername;
            helper.setFrom(from, fromName);
            helper.setTo(destinataire);
            helper.setSubject(objet);
            helper.setText(texte, html);
            mailSender.send(message);
            log.info("E-mail envoyé (objet sans secret) vers un destinataire configuré.");
        } catch (Exception ex) {
            log.warn("Échec d’envoi SMTP : {}", sanitizeSmtpError(ex));
            throw new BusinessException("Impossible d’envoyer l’e-mail. Réessayez plus tard.", 503);
        }
    }

    private static String layout(String title, String inner) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#050b1f;padding:28px 12px;margin:0;">
                  <tr><td align="center">
                    <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;background:#0f1a42;border:1px solid #c9a22755;border-radius:18px;">
                      <tr><td style="padding:28px 32px 12px;text-align:center;border-bottom:1px solid #f6d06a33;">
                        <p style="margin:0;letter-spacing:0.38em;font-size:11px;color:#f6d06a;font-family:Georgia,serif;">MINDSET ALLIANCE</p>
                        <h1 style="margin:10px 0 0;font-size:22px;color:#fff8e7;font-family:Georgia,serif;font-weight:normal;">%s</h1>
                      </td></tr>
                      <tr><td style="padding:28px 32px 8px;font-family:Arial,Helvetica,sans-serif;">%s</td></tr>
                      <tr><td style="padding:8px 32px 28px;font-family:Arial,Helvetica,sans-serif;color:#7d89a8;font-size:11px;line-height:1.5;">
                        MA Workspace — usage interne Mindset Alliance. Ne transmettez jamais votre code ni votre mot de passe.
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                """.formatted(escape(title), inner);
    }

    private static String cta(String label, String href) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 4px;\"><tr><td style=\"background:#f6d06a;border-radius:999px;\">"
                + "<a href=\"" + escape(href) + "\" style=\"display:inline-block;padding:12px 22px;color:#0a1332;text-decoration:none;font-weight:bold;font-size:14px;font-family:Arial,Helvetica,sans-serif;\">"
                + escape(label) + "</a></td></tr></table>";
    }

    private static String badge(String code) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:12px 0;\"><tr><td align=\"center\" style=\"background:#16215c;border:1px solid #f6d06a88;border-radius:14px;padding:18px;\">"
                + "<p style=\"margin:0 0 6px;letter-spacing:0.28em;font-size:10px;color:#f6d06a;font-family:Arial,sans-serif;\">CODE</p>"
                + "<p style=\"margin:0;font-size:32px;letter-spacing:0.28em;color:#fff8e7;font-family:Georgia,serif;\">" + escape(code) + "</p>"
                + "</td></tr></table>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String sanitizeSmtpError(Exception ex) {
        StringBuilder out = new StringBuilder(ex.getClass().getSimpleName());
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 4) {
            String msg = current.getMessage();
            if (msg != null && !msg.isBlank()) {
                out.append(" | ").append(redactSecrets(msg));
            }
            current = current.getCause();
            depth++;
        }
        return out.toString();
    }

    private String redactSecrets(String message) {
        String redacted = message;
        if (StringUtils.hasText(smtpPassword)) {
            redacted = redacted.replace(smtpPassword, "***");
            redacted = redacted.replace(smtpPassword.replace(" ", ""), "***");
        }
        if (StringUtils.hasText(smtpUsername)) {
            redacted = redacted.replace(smtpUsername, "***");
        }
        return redacted;
    }
}
