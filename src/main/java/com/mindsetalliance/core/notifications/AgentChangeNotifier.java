package com.mindsetalliance.core.notifications;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.AgentRole;
import com.mindsetalliance.core.iam.AgentRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AgentChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(AgentChangeNotifier.class);
    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.FRANCE);
    private static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");

    private final AgentRepository agentRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public AgentChangeNotifier(AgentRepository agentRepository, AgentRoleRepository agentRoleRepository,
                               NotificationService notificationService, EmailService emailService) {
        this.agentRepository = agentRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    public String snapshot(Long agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return "";
        }
        return fingerprint(agent);
    }

    public void notifyIfChanged(Long agentId, String before) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return;
        }
        String after = fingerprint(agent);
        if (before == null || before.equals(after)) {
            return;
        }
        String auteur = auteurCourant();
        String date = ZonedDateTime.now(ZONE).format(DATE_FR);
        String titre = "Vos accès ont été mis à jour";
        String message = "Vos permissions ont été mises à jour le " + date + " par " + auteur + ".";
        String avantHumain = humanize(before);
        String apresHumain = humanize(after);
        String email = agent.getEmailPro();
        String nom = nomComplet(agent);
        runAfterCommit(() -> {
            notificationService.send(agentId, "IN_APP", titre, message);
            emailService.envoyerChangementAccesAsync(email, nom, avantHumain, apresHumain, date, auteur);
        });
    }

    public void notifyDeactivated(Long agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return;
        }
        String date = ZonedDateTime.now(ZONE).format(DATE_FR);
        String titre = "Votre compte a été désactivé";
        String message = "Votre accès MA Workspace a été désactivé le " + date + ".";
        String email = agent.getEmailPro();
        String nom = nomComplet(agent);
        runAfterCommit(() -> {
            notificationService.send(agentId, "IN_APP", titre, message);
            emailService.envoyerDesactivationAsync(email, nom, date);
        });
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
            log.warn("Notification agent non livrée : {}", ex.getClass().getSimpleName());
        }
    }

    private String fingerprint(Agent agent) {
        String dept = agent.getDepartement() == null ? "" : String.valueOf(agent.getDepartement().getNom());
        String roles = agentRoleRepository.findByAgentId(agent.getId()).stream()
                .map(this::assignmentLabel)
                .sorted()
                .collect(Collectors.joining(" · "));
        return dept + "||" + roles + "||" + String.valueOf(agent.getStatut());
    }

    private String humanize(String fingerprint) {
        String[] parts = fingerprint.split("\\|\\|", -1);
        String dept = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "Non renseigné";
        String roles = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "Aucun rôle";
        return "Département : " + dept + "\nRôles et périmètre : " + roles;
    }

    private String assignmentLabel(AgentRole assignment) {
        String role = roleLabel(assignment.getRole() == null ? "" : assignment.getRole().getNom());
        if (assignment.getProject() == null) {
            return role + " (toute l’entreprise)";
        }
        String projet = assignment.getProject().getNom() != null
                ? assignment.getProject().getNom()
                : assignment.getProject().getCode();
        return role + " (" + projet + ")";
    }

    private static String roleLabel(String nom) {
        return switch (nom) {
            case "ADMIN_SYSTEME" -> "Administration système";
            case "CONSEIL_ADMINISTRATION" -> "Conseil d’administration";
            case "DEV" -> "Développement";
            case "DIRECTION" -> "Direction";
            case "FINANCE" -> "Finance";
            case "JURIDIQUE" -> "Juridique";
            case "MARKETING" -> "Marketing";
            case "RH" -> "Ressources humaines";
            case "SUPPORT" -> "Support";
            case "COMMERCIAL" -> "Commercial";
            default -> nom;
        };
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
