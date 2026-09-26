package com.mindsetalliance.core.vitrine;

import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.iam.ProjectRepository;
import com.mindsetalliance.core.tickets.TicketService;
import com.mindsetalliance.core.tickets.TicketSysteme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ma.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectEventListener {

    private final VitrineKpiRepository kpiRepository;
    private final ProjectRepository projectRepository;
    private final TicketService ticketService;

    public ProjectEventListener(VitrineKpiRepository kpiRepository,
                                ProjectRepository projectRepository,
                                TicketService ticketService) {
        this.kpiRepository = kpiRepository;
        this.projectRepository = projectRepository;
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = "${ma.kafka.topics.courses}", groupId = "ma-core")
    public void onCourseEvent(Map<String, Object> event) {
        String type = String.valueOf(event.get("type"));
        String projectCode = String.valueOf(event.getOrDefault("projectCode", "CNN"));
        switch (type) {
            case "COURSE_CREEE" -> bump(projectCode, "OPERATIONNEL", "courses_creees", number(event.get("delta"), 1));
            case "COURSE_LIVREE" -> bump(projectCode, "OPERATIONNEL", "courses_livrees", number(event.get("delta"), 1));
            case "COURSE_REFUSEE" -> bump(projectCode, "OPERATIONNEL", "courses_refusees", number(event.get("delta"), 1));
            case "INCIDENT_SIGNALE" -> {
                bump(projectCode, "OPERATIONNEL", "incidents", number(event.get("delta"), 1));
                createTicketIfNeeded(event, projectCode);
            }
            default -> bump(projectCode, "OPERATIONNEL", type.toLowerCase(), number(event.get("valeur"), 1));
        }
    }

    @KafkaListener(topics = "${ma.kafka.topics.coursiers}", groupId = "ma-core")
    public void onCoursierEvent(Map<String, Object> event) {
        String projectCode = String.valueOf(event.getOrDefault("projectCode", "CNN"));
        String type = String.valueOf(event.get("type"));
        bump(projectCode, "OPERATIONNEL", type.toLowerCase(), number(event.get("delta"), 1));
    }

    @KafkaListener(topics = "${ma.kafka.topics.contenus}", groupId = "ma-core")
    public void onContenuEvent(Map<String, Object> event) {
        String projectCode = String.valueOf(event.getOrDefault("projectCode", "MDR"));
        String type = String.valueOf(event.get("type"));
        if ("STATISTIQUES_MISES_A_JOUR".equals(type)) {
            upsert(projectCode, "AUDIENCE", "vues_total", number(event.get("vues"), 0));
            upsert(projectCode, "AUDIENCE", "abonnes_gagnes", number(event.get("abonnesGagnes"), 0));
        } else {
            bump(projectCode, "AUDIENCE", type.toLowerCase(), number(event.get("delta"), 1));
        }
    }

    private void createTicketIfNeeded(Map<String, Object> event, String projectCode) {
        Object titre = event.getOrDefault("titre", "Incident Colis na Nga");
        ticketService.createFromIncident(new TicketService.TicketCreateRequest(
                projectCode,
                null,
                "INCIDENT",
                "ELEVEE",
                String.valueOf(titre),
                String.valueOf(event.getOrDefault("description", "Incident signalé depuis Colis na Nga")),
                null,
                event.get("ticketReference") == null ? null : String.valueOf(event.get("ticketReference")),
                TicketSysteme.SUPPORT,
                "RETARD",
                "COLIS_NA_NGA",
                event.get("ticketReference") == null ? null : String.valueOf(event.get("ticketReference")),
                null,
                null,
                null,
                null
        ), null);
    }

    @Transactional
    public void bump(String projectCode, String categorie, String cle, BigDecimal delta) {
        Project project = projectRepository.findByCode(projectCode).orElse(null);
        if (project == null) {
            return;
        }
        VitrineKpi kpi = kpiRepository.findByProjectIdAndCategorieAndCle(project.getId(), categorie, cle)
                .orElseGet(() -> {
                    VitrineKpi created = new VitrineKpi();
                    created.setProject(project);
                    created.setCategorie(categorie);
                    created.setCle(cle);
                    created.setValeur(BigDecimal.ZERO);
                    return created;
                });
        BigDecimal current = kpi.getValeur() == null ? BigDecimal.ZERO : kpi.getValeur();
        kpi.setValeur(current.add(delta));
        kpi.setDerniereMaj(Instant.now());
        kpiRepository.save(kpi);
    }

    @Transactional
    public void upsert(String projectCode, String categorie, String cle, BigDecimal valeur) {
        Project project = projectRepository.findByCode(projectCode).orElse(null);
        if (project == null) {
            return;
        }
        VitrineKpi kpi = kpiRepository.findByProjectIdAndCategorieAndCle(project.getId(), categorie, cle)
                .orElseGet(() -> {
                    VitrineKpi created = new VitrineKpi();
                    created.setProject(project);
                    created.setCategorie(categorie);
                    created.setCle(cle);
                    return created;
                });
        kpi.setValeur(valeur);
        kpi.setDerniereMaj(Instant.now());
        kpiRepository.save(kpi);
    }

    private BigDecimal number(Object value, int fallback) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return BigDecimal.valueOf(fallback);
            }
        }
        return BigDecimal.valueOf(fallback);
    }
}
