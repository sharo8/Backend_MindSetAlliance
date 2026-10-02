package com.mindsetalliance.core.notifications;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.RoleMatrixImpact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class RoleMatrixNotifier {

    private static final Logger log = LoggerFactory.getLogger(RoleMatrixNotifier.class);
    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);
    private static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");

    private final AgentRepository agentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public RoleMatrixNotifier(AgentRepository agentRepository, NotificationService notificationService,
                              EmailService emailService) {
        this.agentRepository = agentRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    public void notifyAfterCommit(List<RoleMatrixImpact.AgentDigest> digests) {
        if (digests == null || digests.isEmpty()) {
            return;
        }
        String auteur = auteurCourant();
        String date = ZonedDateTime.now(ZONE).format(DATE_FR);
        for (RoleMatrixImpact.AgentDigest digest : digests) {
            try {
                String rolesPhrase = rolePhrase(digest);
                String details = details(digest);
                String titre = "Permissions de votre rôle mises à jour";
                String message = "Les permissions associées à " + rolesPhrase + " ont été mises à jour le "
                        + date + " par " + auteur + ".";
                notificationService.send(digest.agentId(), "IN_APP", titre, message + " " + details.replace("\n", " "));
            } catch (Exception ex) {
                log.warn("Notification in-app matrice non enregistrée : {}", ex.getClass().getSimpleName());
            }
        }
        runAfterCommit(() -> {
            for (RoleMatrixImpact.AgentDigest digest : digests) {
                try {
                    emailService.envoyerMatricePermissionsAsync(
                            digest.email(),
                            digest.nomComplet(),
                            rolePhrase(digest),
                            details(digest),
                            date,
                            auteur);
                } catch (Exception ex) {
                    log.warn("E-mail matrice non livré pour un agent : {}", ex.getClass().getSimpleName());
                }
            }
        });
    }

    static String rolePhrase(RoleMatrixImpact.AgentDigest digest) {
        List<String> names = digest.roles().stream()
                .map(RoleMatrixImpact.RoleDelta::roleLibelle)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        if (names.isEmpty()) {
            return "votre rôle";
        }
        if (names.size() == 1) {
            return "votre rôle " + names.getFirst();
        }
        return "vos rôles " + String.join(" et ", names);
    }

    static String details(RoleMatrixImpact.AgentDigest digest) {
        StringBuilder out = new StringBuilder();
        for (RoleMatrixImpact.RoleDelta role : digest.roles()) {
            if (digest.roles().size() > 1) {
                out.append(role.roleLibelle()).append('\n');
            }
            for (RoleMatrixImpact.PermLine line : role.added()) {
                out.append("+ ").append(line.libelle()).append('\n');
            }
            for (RoleMatrixImpact.PermLine line : role.removed()) {
                out.append("− ").append(line.libelle()).append('\n');
            }
        }
        return out.toString().trim();
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeRun(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeRun(action);
            }
        });
    }

    private void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("Notification matrice non livrée : {}", ex.getClass().getSimpleName());
        }
    }

    private String auteurCourant() {
        try {
            Long id = JwtRoles.agentId();
            return agentRepository.findById(id)
                    .map(this::nomComplet)
                    .filter(s -> !s.isBlank())
                    .orElse("Administration");
        } catch (Exception ex) {
            return "Administration";
        }
    }

    private String nomComplet(Agent agent) {
        return ((agent.getPrenom() == null ? "" : agent.getPrenom()) + " "
                + (agent.getNom() == null ? "" : agent.getNom())).trim();
    }
}
