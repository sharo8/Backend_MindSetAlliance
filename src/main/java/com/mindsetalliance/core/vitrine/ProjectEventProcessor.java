package com.mindsetalliance.core.vitrine;

import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.iam.ProjectRepository;
import com.mindsetalliance.core.tickets.TicketService;
import com.mindsetalliance.core.tickets.TicketSysteme;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Traduction des événements des projets externes en projections {@code vitrine_kpis}.
 *
 * Point d'entrée unique, quel que soit le transport : Kafka ({@link ProjectEventListener})
 * ou HTTP signé ({@code integration.cnn.CnnEventIngestController}). Le format JSON des
 * événements est identique dans les deux cas — voir docs du contrat d'intégration.
 */
@Service
public class ProjectEventProcessor {

    public static final String TOPIC_COURSES = "colisnanga.courses.events";
    public static final String TOPIC_COURSIERS = "colisnanga.coursiers.events";
    public static final String TOPIC_CONTENUS = "mdr.contenus.events";

    private final VitrineKpiRepository kpiRepository;
    private final ProjectRepository projectRepository;
    private final TicketService ticketService;

    public ProjectEventProcessor(VitrineKpiRepository kpiRepository,
                                 ProjectRepository projectRepository,
                                 TicketService ticketService) {
        this.kpiRepository = kpiRepository;
        this.projectRepository = projectRepository;
        this.ticketService = ticketService;
    }

    /** @return false si le topic est inconnu (l'événement est ignoré). */
    public boolean process(String topic, Map<String, Object> event) {
        switch (topic) {
            case TOPIC_COURSES -> onCourseEvent(event);
            case TOPIC_COURSIERS -> onCoursierEvent(event);
            case TOPIC_CONTENUS -> onContenuEvent(event);
            default -> {
                return false;
            }
        }
        return true;
    }

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

    public void onCoursierEvent(Map<String, Object> event) {
        String projectCode = String.valueOf(event.getOrDefault("projectCode", "CNN"));
        String type = String.valueOf(event.get("type"));
        bump(projectCode, "OPERATIONNEL", type.toLowerCase(), number(event.get("delta"), 1));
        if ("ABONNEMENT_REGLE".equals(type) && event.get("montant") != null) {
            bump(projectCode, "FINANCE", "abonnements_montant", number(event.get("montant"), 0));
        }
    }

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

    private static final java.util.Set<String> CATEGORIES_SUPPORT =
            java.util.Set.of("REFUS_COURSE", "RETARD", "LITIGE_PAIEMENT", "DESTINATAIRE_INJOIGNABLE");

    private void createTicketIfNeeded(Map<String, Object> event, String projectCode) {
        Object titre = event.getOrDefault("titre", "Incident Colis na Nga");
        String categorie = String.valueOf(event.getOrDefault("categorie", "RETARD")).toUpperCase();
        if (!CATEGORIES_SUPPORT.contains(categorie)) {
            categorie = "RETARD";
        }
        Object reference = event.get("ticketReference") != null ? event.get("ticketReference") : event.get("courseId");
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
                categorie,
                "COLIS_NA_NGA",
                reference == null ? null : String.valueOf(reference),
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
        VitrineKpi kpi = findOrCreate(project, categorie, cle);
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
        VitrineKpi kpi = findOrCreate(project, categorie, cle);
        kpi.setValeur(valeur);
        kpi.setDerniereMaj(Instant.now());
        kpiRepository.save(kpi);
    }

    private VitrineKpi findOrCreate(Project project, String categorie, String cle) {
        return kpiRepository.findByProjectIdAndCategorieAndCle(project.getId(), categorie, cle)
                .orElseGet(() -> {
                    VitrineKpi created = new VitrineKpi();
                    created.setProject(project);
                    created.setCategorie(categorie);
                    created.setCle(cle);
                    created.setValeur(BigDecimal.ZERO);
                    return created;
                });
    }

    public static BigDecimal number(Object value, int fallback) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
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
