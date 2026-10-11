package com.mindsetalliance.core.cnn;

import com.mindsetalliance.core.vitrine.VitrineKpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Agrégats SELECT sur la base Colis na Nga. Aucun nom de client, téléphone ou adresse.
 * « Aujourd'hui » est le jour civil Africa/Kinshasa. Les datetime naïfs de leur base
 * sont interprétés dans ce fuseau (leur application écrit des LocalDateTime).
 */
@Service
public class CnnDatabaseReader {

    static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");
    public static final String ABSENT = "Non disponible dans la base Colis na Nga";
    private static final Logger log = LoggerFactory.getLogger(CnnDatabaseReader.class);
    private static final Set<String> READ_MODULES = Set.of("COURSE", "INCIDENT", "COURIER", "SUBSCRIPTION");
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);
    private static final List<String> DELAY_BINS = List.of("0-1h", "1-4h", "4-24h", "1-3j", "3j+");
    private static final Map<String, String> STATUT_LABELS = Map.of(
            "DELIVERED", "Livrée",
            "AGENT_EN_ROUTE", "Agent en route",
            "IN_TRANSIT", "En transit",
            "COLLECTED", "Colis collecté",
            "COURIER_AT_PICKUP", "Coursier au point de collecte",
            "COURIER_AT_DESTINATION", "Coursier à destination",
            "CANCELLED", "Annulée"
    );
    private static final Map<String, String> STATUT_COLORS = Map.of(
            "DELIVERED", "#16a34a",
            "AGENT_EN_ROUTE", "#2563eb",
            "IN_TRANSIT", "#e0a721",
            "COLLECTED", "#16215c",
            "COURIER_AT_PICKUP", "#7c3aed",
            "COURIER_AT_DESTINATION", "#0891b2",
            "CANCELLED", "#dc2626"
    );

    private static final String ZONE_MESSAGE = ABSENT;
    private static final String RETARDS_MESSAGE = ABSENT;
    private static final String REFUS_MESSAGE = ABSENT;
    private static final String COURSIERS_EXPIRES_MESSAGE = ABSENT;

    private final CnnProperties properties;
    private final CnnReadOnlyClient cnn;
    private final CnnSourceStatus sourceStatus;
    private final Object cacheLock = new Object();
    private volatile Cache cache;

    @Autowired
    public CnnDatabaseReader(CnnProperties properties,
                             @Qualifier("cnnJdbcTemplate") ObjectProvider<JdbcTemplate> cnnJdbc,
                             ObjectProvider<CnnSourceStatus> sourceStatus) {
        this(properties, cnnJdbc.getIfAvailable(), sourceStatus.getIfAvailable());
    }

    CnnDatabaseReader(CnnProperties properties, JdbcTemplate jdbc) {
        this(properties, jdbc, null);
    }

    CnnDatabaseReader(CnnProperties properties, JdbcTemplate jdbc, CnnSourceStatus sourceStatus) {
        this.properties = properties;
        this.cnn = jdbc == null ? null : new CnnReadOnlyClient(jdbc);
        this.sourceStatus = sourceStatus;
    }

    public boolean dbMode() {
        return properties.dbMode();
    }

    public boolean applies(String projectCode) {
        return projectCode != null && "CNN".equalsIgnoreCase(projectCode);
    }

    public boolean readModule(String module) {
        return module != null && READ_MODULES.contains(module.toUpperCase());
    }

    public CnnSnapshot snapshot() {
        if (cnn == null) {
            return CnnSnapshot.unavailable("Source Colis na Nga indisponible.");
        }
        int seconds = Math.max(properties.getCacheSeconds(), 0);
        Cache hit = cache;
        if (seconds > 0 && hit != null && hit.expiresAt.isAfter(Instant.now())) {
            return hit.value;
        }
        synchronized (cacheLock) {
            hit = cache;
            if (seconds > 0 && hit != null && hit.expiresAt.isAfter(Instant.now())) {
                return hit.value;
            }
            CnnSnapshot fresh = load();
            if (seconds > 0) {
                cache = new Cache(fresh, Instant.now().plusSeconds(seconds));
            }
            return fresh;
        }
    }

    public Map<String, Object> dashboard(String projectCode) {
        CnnSnapshot snap = snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coursesDuJour", snap.coursesCreeesJour());
        body.put("coursesLivreesJour", snap.coursesLivreesJour());
        body.put("coursesLivreesPeriode", snap.coursesLivreesPeriode());
        body.put("coursesLivreesTotal", snap.coursesLivreesTotal());
        body.put("coursesAnnulees", snap.coursesAnnulees());
        body.put("coursiersActifs", snap.coursiersActifs());
        body.put("coursiersExpires", snap.coursiersExpires());
        body.put("coursiersExpiresMessage", snap.coursiersExpiresMessage());
        body.put("tauxLivraison", taux(snap));
        body.put("incidentsOuverts", snap.incidentsOuverts());
        body.put("urgences", snap.urgences());
        body.put("abonnementsRegles", snap.abonnementsRegles());
        body.put("abonnementsMontant", snap.abonnementsMontant());
        body.put("abonnementsExpires", snap.abonnementsExpires());
        body.put("live", snap.available());
        body.put("disponible", snap.available());
        body.put("source", snap.available() ? "CNN_DB" : "INDISPONIBLE");
        body.put("readOnlyVerified", sourceStatus != null && sourceStatus.readOnlyVerified());
        body.put("message", snap.message());
        body.put("scope", projectCode);
        body.put("volume30j", snap.volume30j());
        body.put("volumeMessage", snap.volumeMessage());
        body.put("parZone", List.of());
        body.put("zoneDisponible", false);
        body.put("zoneMessage", ZONE_MESSAGE);
        body.put("parStatut", snap.parStatut());
        body.put("retardsMessage", RETARDS_MESSAGE);
        body.put("refusMessage", REFUS_MESSAGE);
        body.put("derniereCourse", snap.derniereCourse());
        body.put("derniereLivraison", snap.derniereLivraison());
        body.put("livraisonJourMessage", livraisonJourMessage(snap));
        body.put("incidents", snap.incidents());
        body.put("calculeLe", snap.calculeLe());
        body.put("ageSecondes", age(snap));
        body.put("parStatutLibelles", snap.parStatut());
        return body;
    }

    public Map<String, Object> accueil(String projectCode) {
        CnnSnapshot snap = snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vue", "CNN");
        body.put("vuesDisponibles", List.of("CNN"));
        body.put("titreActivite", "Courses créées, 7 derniers jours");
        body.put("titreRepartition", "Répartition des courses par statut");
        body.put("titreHistogramme", "Délai de livraison");
        body.put("uniteActivite", "courses");
        body.put("activiteParJour", snap.activite7j());
        body.put("activiteMessage", snap.activiteMessage());
        body.put("repartitionParStatut", repartition(snap));
        body.put("histogrammeDelais", snap.delaiBins());
        body.put("histogrammeDisponible", snap.available() && snap.delaiDisponible());
        body.put("histogrammeMessage", snap.available() ? snap.delaiMessage() : snap.message());
        body.put("syntheseTendance", livraisonJourMessage(snap));
        body.put("source", snap.available() ? "CNN_DB" : "INDISPONIBLE");
        body.put("readOnlyVerified", sourceStatus != null && sourceStatus.readOnlyVerified());
        body.put("disponible", snap.available());
        body.put("message", snap.message());
        body.put("scope", projectCode);
        body.put("coursesLivreesJour", snap.coursesLivreesJour());
        body.put("coursesLivreesTotal", snap.coursesLivreesTotal());
        body.put("coursiersActifs", snap.coursiersActifs());
        body.put("abonnementsRegles", snap.abonnementsRegles());
        body.put("livraisonJourMessage", livraisonJourMessage(snap));
        body.put("calculeLe", snap.calculeLe());
        body.put("ageSecondes", age(snap));
        return body;
    }

    public Map<String, Object> finance(String projectCode) {
        CnnSnapshot snap = snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", snap.available() ? "CNN_DB" : "INDISPONIBLE");
        body.put("readOnlyVerified", sourceStatus != null && sourceStatus.readOnlyVerified());
        body.put("disponible", snap.available());
        body.put("messageCode", snap.available() ? null : "unavailable");
        body.put("scope", projectCode);
        body.put("recettesParDevise", snap.recettesParDevise());
        body.put("serie6m", snap.serie6m());
        body.put("parCanal", snap.parCanal());
        body.put("remboursements", snap.remboursements());
        body.put("volumeAffaires", snap.volumeAffaires());
        body.put("calculeLe", snap.calculeLe());
        body.put("ageSecondes", age(snap));
        return body;
    }

    public Map<String, Object> direction(String projectCode) {
        CnnSnapshot snap = snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", snap.available() ? "CNN_DB" : "INDISPONIBLE");
        body.put("readOnlyVerified", sourceStatus != null && sourceStatus.readOnlyVerified());
        body.put("disponible", snap.available());
        body.put("messageCode", snap.available() ? null : "unavailable");
        body.put("scope", projectCode);
        body.put("coursesParMois", snap.coursesParMois());
        body.put("parStatut", snap.parStatut());
        body.put("remboursementsEnAttente", snap.incidentsOuverts());
        body.put("appelsUrgence", snap.urgences());
        body.put("calculeLe", snap.calculeLe());
        body.put("ageSecondes", age(snap));
        return body;
    }

    public List<Map<String, Object>> list(String module) {
        CnnSnapshot snap = snapshot();
        if (!snap.available()) {
            return List.of(row(module, "Source indisponible", "INDISPONIBLE", snap.message(), null));
        }
        return switch (module.toUpperCase()) {
            case "COURSE" -> courses(snap);
            case "INCIDENT" -> snap.incidents().isEmpty()
                    ? List.of(row(module, "Aucun incident ouvert", "VIDE", incidentVide(snap), null))
                    : snap.incidents();
            case "COURIER" -> coursiers(snap);
            case "SUBSCRIPTION" -> abonnements(snap);
            default -> List.of();
        };
    }

    public List<?> overlayVitrine(List<VitrineKpi> rows) {
        boolean cnn = rows.stream().anyMatch(kpi -> kpi.getProject() != null && "CNN".equalsIgnoreCase(kpi.getProject().getCode()));
        if (!cnn) {
            return rows;
        }
        CnnSnapshot snap = snapshot();
        List<Object> out = new ArrayList<>();
        VitrineKpi cnnSeed = null;
        java.util.Set<String> emitted = new java.util.HashSet<>();
        for (VitrineKpi kpi : rows) {
            if (kpi.getProject() != null && "CNN".equalsIgnoreCase(kpi.getProject().getCode())) {
                if (cnnSeed == null) {
                    cnnSeed = kpi;
                }
                if (!"courses_livrees_jour".equals(kpi.getCle())) {
                    continue;
                }
                Object valeur = snap.available() ? snap.coursesLivreesJour() : null;
                emitted.add(kpi.getCle());
                out.add(kpiView(kpi.getId(), kpi, kpi.getCle(), valeur));
            } else {
                out.add(kpi);
            }
        }
        if (snap.available() && cnnSeed != null) {
            if (emitted.add("coursiers_actifs")) {
                out.add(kpiView(-1L, cnnSeed, "coursiers_actifs", snap.coursiersActifs()));
            }
            if (emitted.add("courses_livrees")) {
                out.add(kpiView(-2L, cnnSeed, "courses_livrees", snap.coursesLivreesTotal()));
            }
            if (snap.abonnementsRegles() != null && emitted.add("abonnements_regles")) {
                out.add(kpiView(-3L, cnnSeed, "abonnements_regles", snap.abonnementsRegles()));
            }
        }
        return out;
    }

    private CnnSnapshot load() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = today.plusDays(1).atStartOfDay();
        LocalDateTime startPeriod = today.minusDays(29).atStartOfDay();
        try {
            Draft draft = new Draft();
            draft.coursesTotal = count("SELECT COUNT(*) FROM orders");
            draft.coursesCreeesJour = count(
                    "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ?", startToday, endToday);
            draft.coursesAnnulees = count("SELECT COUNT(*) FROM orders WHERE order_status = 'CANCELLED'");
            draft.coursesLivreesTotal = count("SELECT COUNT(*) FROM orders WHERE order_status = 'DELIVERED'");
            draft.coursesLivreesJour = livreesEntre(startToday, endToday);
            draft.coursesLivreesPeriode = livreesEntre(startPeriod, endToday);
            draft.parStatut = statuts();
            draft.volume30j = volume(today, startPeriod, endToday);
            draft.derniereCourse = derniereCourse();
            draft.volumeMessage = volumeMessage(draft);
            Long motards = count("""
                    SELECT COUNT(*) FROM partner_motards
                    WHERE active = TRUE AND validation_status = 'APPROVED'
                      AND (courses_suspended IS NULL OR courses_suspended = FALSE)
                    """);
            Long agents = count("SELECT COUNT(*) FROM delivery_agents WHERE active = TRUE");
            draft.coursiersActifs = sumKnown(motards, agents);
            draft.motardsAttente = count(
                    "SELECT COUNT(*) FROM partner_motards WHERE validation_status = 'PENDING_VALIDATION'");
            draft.agentsActifs = agents;
            CountSum credits = countSum("""
                    SELECT COUNT(*), COALESCE(SUM(amount_paid), 0)
                    FROM motard_credit_subscriptions
                    WHERE is_free_starter = FALSE AND amount_paid > 0
                    """);
            CountSum paiements = countSum("""
                    SELECT COUNT(*), COALESCE(SUM(amount), 0)
                    FROM subscription_payments WHERE status = 'COMPLETED'
                    """);
            draft.abonnementsRegles = sumKnown(
                    credits == null ? null : credits.count(),
                    paiements == null ? null : paiements.count());
            draft.abonnementsMontant = sumMoney(credits, paiements);
            Long paiementsExpires = count("SELECT COUNT(*) FROM subscription_payments WHERE status = 'EXPIRED'");
            Long subsExpires = count("""
                    SELECT COUNT(*) FROM subscriptions
                    WHERE expiration_date IS NOT NULL AND expiration_date < ?
                    """, today);
            draft.abonnementsExpires = sumKnown(paiementsExpires, subsExpires);
            draft.incidentsOuverts = count("SELECT COUNT(*) FROM refunds WHERE status = 'PENDING'");
            draft.urgences = count("SELECT COUNT(*) FROM emergency_calls");
            draft.incidents = incidentRows(draft);
            draft.derniereLivraison = maxDate("SELECT MAX(timestamp) FROM status_histories WHERE status = 'DELIVERED'");
            draft.activite7j = activite7(today);
            draft.activiteMessage = activiteMessage(draft);
            List<Map<String, Object>> delais = delais();
            draft.delaiDisponible = delais != null;
            draft.delaiBins = delais == null ? List.of() : delais;
            draft.delaiMessage = delais == null ? ABSENT : null;
            remplirFinance(draft, today);
            draft.coursesParMois = coursesParMois(today);
            return draft.build();
        } catch (DataAccessException ex) {
            log.warn("Source Colis na Nga indisponible : {}", root(ex));
            return CnnSnapshot.unavailable("Source Colis na Nga indisponible.");
        }
    }

    private Long livreesEntre(LocalDateTime from, LocalDateTime to) {
        Long viaColis = count("""
                SELECT COUNT(DISTINCT COALESCE(h.order_id, p.order_id))
                FROM status_histories h
                LEFT JOIN parcels p ON p.id = h.parcel_id
                WHERE h.status = 'DELIVERED' AND h.timestamp >= ? AND h.timestamp < ?
                """, from, to);
        if (viaColis != null) {
            return viaColis;
        }
        Long viaOrder = count("""
                SELECT COUNT(DISTINCT order_id) FROM status_histories
                WHERE status = 'DELIVERED' AND order_id IS NOT NULL
                  AND timestamp >= ? AND timestamp < ?
                """, from, to);
        if (viaOrder != null) {
            return viaOrder;
        }
        return count("""
                SELECT COUNT(*) FROM orders
                WHERE order_status = 'DELIVERED' AND updated_at >= ? AND updated_at < ?
                """, from, to);
    }

    private List<Map<String, Object>> statuts() {
        try {
            return cnn.query("""
                    SELECT order_status, COUNT(*) AS volume
                    FROM orders
                    GROUP BY order_status
                    ORDER BY order_status
                    """, (rs, i) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                String code = rs.getString(1);
                row.put("statut", code);
                row.put("libelle", label(code));
                row.put("volume", rs.getLong(2));
                row.put("couleur", STATUT_COLORS.getOrDefault(code, "#64748b"));
                return row;
            });
        } catch (BadSqlGrammarException ex) {
            log.info("Statuts CNN ignorés : {}", root(ex));
            return List.of();
        }
    }

    private List<Map<String, Object>> volume(LocalDate today, LocalDateTime from, LocalDateTime to) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        try {
            cnn.query("""
                    SELECT CAST(created_at AS DATE) AS jour, COUNT(*) AS volume
                    FROM orders
                    WHERE created_at >= ? AND created_at < ?
                    GROUP BY CAST(created_at AS DATE)
                    """, (ResultSet rs) -> {
                while (rs.next()) {
                    if (rs.getDate(1) != null) {
                        byDay.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
                    }
                }
                return null;
            }, from, to);
        } catch (BadSqlGrammarException ex) {
            log.info("Volume CNN ignoré : {}", root(ex));
            return List.of();
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate day = from.toLocalDate(); !day.isAfter(today); day = day.plusDays(1)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("jour", day.toString());
            point.put("volume", byDay.getOrDefault(day, 0L));
            series.add(point);
        }
        return series;
    }

    private String derniereCourse() {
        try {
            Timestamp at = cnn.queryForObject("SELECT MAX(created_at) FROM orders", Timestamp.class);
            if (at == null) {
                return null;
            }
            return at.toLocalDateTime().toLocalDate().toString();
        } catch (BadSqlGrammarException ex) {
            log.info("Date de dernière course ignorée : {}", root(ex));
            return null;
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private String volumeMessage(Draft draft) {
        long somme = draft.volume30j.stream().mapToLong(p -> ((Number) p.get("volume")).longValue()).sum();
        if (somme == 0 && draft.coursesTotal != null && draft.coursesTotal > 0) {
            String date = draft.derniereCourse == null ? "inconnue" : draft.derniereCourse;
            return "Aucune course créée sur les 30 derniers jours — dernière course le " + formatFr(date) + ".";
        }
        return null;
    }

    private List<Map<String, Object>> incidentRows(Draft draft) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (draft.urgences != null && draft.urgences > 0) {
            rows.add(row("INCIDENT", "Appels d'urgence", "SIGNALE", draft.urgences + " appel(s), sans motif ni téléphone", null));
        }
        if (draft.incidentsOuverts != null && draft.incidentsOuverts > 0) {
            rows.add(row("INCIDENT", "Remboursements en attente", "OUVERT",
                    draft.incidentsOuverts + " litige(s), sans motif", null));
        }
        if (rows.isEmpty()) {
            rows.add(row("INCIDENT", "Aucun incident ouvert", "VIDE", incidentVide(draft), null));
        }
        return rows;
    }

    private String incidentVide(CnnSnapshot snap) {
        return incidentVide(snap.urgences(), snap.incidentsOuverts());
    }

    private String incidentVide(Draft draft) {
        return incidentVide(draft.urgences, draft.incidentsOuverts);
    }

    private String incidentVide(Long urgences, Long litiges) {
        String urg = urgences == null ? "table des appels d'urgence absente" : "aucun appel d'urgence";
        String lit = litiges == null ? "table des remboursements absente" : "aucun remboursement en attente";
        return Character.toUpperCase(urg.charAt(0)) + urg.substring(1) + ", " + lit
                + ". " + RETARDS_MESSAGE + " " + REFUS_MESSAGE;
    }

    private List<Map<String, Object>> courses(CnnSnapshot snap) {
        if (snap.parStatut().isEmpty()) {
            return List.of(row("COURSE", "Courses", "SANS_DONNEE", "La table des courses est absente ou vide.", null));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> statut : snap.parStatut()) {
            String code = String.valueOf(statut.get("statut"));
            rows.add(row("COURSE", code, code, statut.get("volume") + " courses", null));
        }
        return rows;
    }

    private List<Map<String, Object>> coursiers(CnnSnapshot snap) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (snap.coursiersActifs() == null) {
            rows.add(row("COURIER", "Coursiers actifs", "SANS_DONNEE", "Table des coursiers absente.", null));
        } else {
            String detail = snap.coursiersActifs() + " au total";
            if (snap.agentsActifs() != null) {
                detail = detail + ", dont " + snap.agentsActifs() + " agent(s) livraison";
            }
            rows.add(row("COURIER", "Coursiers actifs", "ACTIF", detail, null));
        }
        if (snap.motardsAttente() != null && snap.motardsAttente() > 0) {
            rows.add(row("COURIER", "Coursiers en validation", "EN_VALIDATION", snap.motardsAttente() + " en attente", null));
        }
        rows.add(row("COURIER", "Expiration des coursiers", "SANS_DONNEE", COURSIERS_EXPIRES_MESSAGE, null));
        return rows;
    }

    private List<Map<String, Object>> abonnements(CnnSnapshot snap) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (snap.abonnementsRegles() == null) {
            rows.add(row("SUBSCRIPTION", "Abonnements réglés", "SANS_DONNEE",
                    "Aucune table d'abonnement exploitable.", null));
        } else {
            rows.add(row("SUBSCRIPTION", "Abonnements réglés", "REGLE",
                    snap.abonnementsRegles() + " règlements", snap.abonnementsMontant()));
        }
        if (snap.abonnementsExpires() == null) {
            rows.add(row("SUBSCRIPTION", "Abonnements expirés", "SANS_DONNEE",
                    "Pas de date d'expiration exploitable.", null));
        } else {
            rows.add(row("SUBSCRIPTION", "Abonnements expirés", "EXPIRE",
                    snap.abonnementsExpires() + " expirés", null));
        }
        return rows;
    }

    private Map<String, Object> row(String module, String titre, String statut, String description, BigDecimal montant) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", Math.abs((module + "|" + titre).hashCode()));
        row.put("module", module);
        row.put("titre", titre);
        row.put("description", description);
        row.put("statut", statut);
        row.put("lectureSeule", true);
        row.put("projectCode", "CNN");
        if (montant != null) {
            row.put("montant", montant);
        }
        return row;
    }

    private Map<String, Object> kpiView(Long id, VitrineKpi seed, String cle, Object valeur) {
        Map<String, Object> project = new LinkedHashMap<>();
        if (seed.getProject() != null) {
            project.put("id", seed.getProject().getId());
            project.put("code", seed.getProject().getCode());
            project.put("nom", seed.getProject().getNom());
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("project", project);
        row.put("categorie", "OPERATIONNEL");
        row.put("cle", cle);
        row.put("valeur", valeur);
        row.put("derniereMaj", Instant.now());
        return row;
    }

    private Double taux(CnnSnapshot snap) {
        if (!snap.available() || snap.coursesTotal() == null || snap.coursesTotal() == 0 || snap.coursesLivreesTotal() == null) {
            return snap.available() ? 0d : null;
        }
        return Math.round(1000.0 * snap.coursesLivreesTotal() / snap.coursesTotal()) / 10.0;
    }

    private Long count(String sql, Object... args) {
        try {
            Long value = cnn.queryForObject(sql, Long.class, args);
            return value == null ? 0L : value;
        } catch (BadSqlGrammarException ex) {
            log.info("Agrégat CNN ignoré (table ou colonne absente) : {}", root(ex));
            return null;
        }
    }

    private CountSum countSum(String sql) {
        try {
            return cnn.query(sql, rs -> {
                if (!rs.next()) {
                    return new CountSum(0, BigDecimal.ZERO);
                }
                BigDecimal sum = rs.getBigDecimal(2);
                return new CountSum(rs.getLong(1), sum == null ? BigDecimal.ZERO : sum);
            });
        } catch (BadSqlGrammarException ex) {
            log.info("Somme CNN ignorée (table ou colonne absente) : {}", root(ex));
            return null;
        }
    }

    private static Long sumKnown(Long left, Long right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0L : left) + (right == null ? 0L : right);
    }

    private static BigDecimal sumMoney(CountSum left, CountSum right) {
        if (left == null && right == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (left != null) {
            total = total.add(left.sum());
        }
        if (right != null) {
            total = total.add(right.sum());
        }
        return total;
    }

    private static String root(DataAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause.getMessage() == null ? ex.getClass().getSimpleName() : cause.getMessage();
        int newline = message.indexOf('\n');
        if (newline >= 0) {
            message = message.substring(0, newline);
        }
        message = message
                .replaceAll("(?i)jdbc:[^\\s]+", "jdbc")
                .replaceAll("(?i)access denied.*", "access denied")
                .replaceAll("(?i)(password|pwd)=[^\\s&]+", "$1");
        return message;
    }

    private List<Map<String, Object>> activite7(LocalDate today) {
        LocalDate from = today.minusDays(6);
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        try {
            cnn.query("""
                    SELECT created_at FROM orders WHERE created_at >= ? AND created_at < ?
                    """, rs -> {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp(1);
                    if (ts != null) {
                        byDay.merge(ts.toLocalDateTime().toLocalDate(), 1L, Long::sum);
                    }
                }
                return null;
            }, from.atStartOfDay(), today.plusDays(1).atStartOfDay());
        } catch (BadSqlGrammarException ex) {
            log.info("Série 7 jours CNN ignorée : {}", root(ex));
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toString());
            row.put("jour", weekday(day.getDayOfWeek()));
            row.put("valeur", byDay.getOrDefault(day, 0L));
            rows.add(row);
        }
        return rows;
    }

    private String activiteMessage(Draft draft) {
        long somme = draft.activite7j.stream().mapToLong(p -> ((Number) p.get("valeur")).longValue()).sum();
        if (somme == 0 && draft.derniereCourse != null) {
            return "Aucune course créée sur les 7 derniers jours — dernière course le " + formatFr(draft.derniereCourse) + ".";
        }
        return null;
    }

    private List<Map<String, Object>> delais() {
        List<Duration> spans = delaiSpans("""
                SELECT o.created_at, MIN(h.timestamp)
                FROM orders o
                JOIN status_histories h ON h.status = 'DELIVERED'
                LEFT JOIN parcels p ON p.id = h.parcel_id
                WHERE h.order_id = o.id OR p.order_id = o.id
                GROUP BY o.id, o.created_at
                """);
        if (spans == null) {
            spans = delaiSpans("""
                    SELECT o.created_at, MIN(h.timestamp)
                    FROM orders o
                    JOIN status_histories h ON h.order_id = o.id AND h.status = 'DELIVERED'
                    GROUP BY o.id, o.created_at
                    """);
        }
        if (spans == null) {
            return null;
        }
        Map<String, Long> bins = new LinkedHashMap<>();
        DELAY_BINS.forEach(bin -> bins.put(bin, 0L));
        for (Duration span : spans) {
            if (span == null || span.isNegative()) {
                continue;
            }
            long minutes = span.toMinutes();
            String bin = minutes < 60 ? "0-1h" : minutes < 240 ? "1-4h" : minutes < 1440 ? "4-24h" : minutes < 4320 ? "1-3j" : "3j+";
            bins.merge(bin, 1L, Long::sum);
        }
        return DELAY_BINS.stream().map(tranche -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tranche", tranche);
            row.put("occurrences", bins.getOrDefault(tranche, 0L));
            return row;
        }).toList();
    }

    private List<Duration> delaiSpans(String sql) {
        try {
            return cnn.query(sql, (rs, i) -> {
                Timestamp created = rs.getTimestamp(1);
                Timestamp delivered = rs.getTimestamp(2);
                if (created == null || delivered == null) {
                    return null;
                }
                return Duration.between(created.toLocalDateTime(), delivered.toLocalDateTime());
            });
        } catch (BadSqlGrammarException ex) {
            log.info("Délai de livraison CNN ignoré : {}", root(ex));
            return null;
        }
    }

    private void remplirFinance(Draft draft, LocalDate today) {
        List<Flux> credits = flux("""
                SELECT purchased_at, amount_paid, payment_mode, NULL
                FROM motard_credit_subscriptions
                WHERE is_free_starter = FALSE AND amount_paid > 0
                """);
        List<Flux> paiements = flux("""
                SELECT created_at, amount, payment_mode, currency
                FROM subscription_payments
                WHERE status = 'COMPLETED'
                """);
        if (credits == null && paiements == null) {
            draft.recettesParDevise = null;
            draft.serie6m = List.of();
            draft.parCanal = List.of();
        } else {
            List<Flux> lignes = new ArrayList<>();
            if (credits != null) {
                lignes.addAll(credits);
            }
            if (paiements != null) {
                lignes.addAll(paiements);
            }
            YearMonth courant = YearMonth.from(today);
            Map<String, List<Flux>> parDevise = new LinkedHashMap<>();
            for (Flux ligne : lignes) {
                parDevise.computeIfAbsent(deviseKey(ligne.devise()), key -> new ArrayList<>()).add(ligne);
            }
            List<Map<String, Object>> recettes = new ArrayList<>();
            List<String> devises = new ArrayList<>(parDevise.keySet());
            for (String key : devises) {
                List<Flux> groupe = parDevise.get(key);
                BigDecimal mois = BigDecimal.ZERO;
                BigDecimal total = BigDecimal.ZERO;
                LocalDate dernier = null;
                for (Flux ligne : groupe) {
                    total = total.add(ligne.amount());
                    if (YearMonth.from(ligne.at()).equals(courant)) {
                        mois = mois.add(ligne.amount());
                    }
                    LocalDate jour = ligne.at().toLocalDate();
                    if (dernier == null || jour.isAfter(dernier)) {
                        dernier = jour;
                    }
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("devise", key.isEmpty() ? null : key);
                row.put("deviseStockee", !key.isEmpty());
                row.put("mois", mois);
                row.put("total", total);
                row.put("dernier", dernier == null ? null : dernier.toString());
                recettes.add(row);
            }
            draft.recettesParDevise = recettes;
            draft.recettesMois = recettes.size() == 1 ? (BigDecimal) recettes.get(0).get("mois") : null;
            draft.dernierReglement = recettes.stream()
                    .map(row -> (String) row.get("dernier"))
                    .filter(date -> date != null)
                    .max(String::compareTo)
                    .orElse(null);
            List<Map<String, Object>> serie = new ArrayList<>();
            for (int i = 5; i >= 0; i--) {
                YearMonth ym = courant.minusMonths(i);
                List<Map<String, Object>> parts = new ArrayList<>();
                for (String key : devises) {
                    BigDecimal montant = parDevise.get(key).stream()
                            .filter(ligne -> YearMonth.from(ligne.at()).equals(ym))
                            .map(Flux::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("devise", key.isEmpty() ? null : key);
                    part.put("deviseStockee", !key.isEmpty());
                    part.put("montant", montant);
                    parts.add(part);
                }
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("mois", ym.toString());
                point.put("montants", parts);
                serie.add(point);
            }
            draft.serie6m = serie;
            draft.encaissements6m = serie;
            draft.parCanal = canaux(parDevise);
        }
        draft.remboursements = remboursements();
        draft.volumeAffaires = volumeAffaires();
    }

    private List<Map<String, Object>> coursesParMois(LocalDate today) {
        try {
            List<LocalDateTime> instants = cnn.query("SELECT created_at FROM orders", (rs, i) -> {
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toLocalDateTime();
            });
            Map<YearMonth, Long> counts = new java.util.TreeMap<>();
            YearMonth max = YearMonth.from(today);
            YearMonth min = null;
            for (LocalDateTime instant : instants) {
                if (instant == null) {
                    continue;
                }
                YearMonth ym = YearMonth.from(instant.toLocalDate());
                counts.merge(ym, 1L, Long::sum);
                if (min == null || ym.isBefore(min)) {
                    min = ym;
                }
            }
            if (min == null) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (YearMonth ym = min; !ym.isAfter(max); ym = ym.plusMonths(1)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("mois", ym.toString());
                row.put("volume", counts.getOrDefault(ym, 0L));
                rows.add(row);
            }
            return rows;
        } catch (BadSqlGrammarException ex) {
            log.info("Courses par mois CNN ignorées : {}", root(ex));
            return List.of();
        }
    }

    private List<Flux> flux(String sql) {
        try {
            return cnn.query(sql, (rs, i) -> {
                Timestamp ts = rs.getTimestamp(1);
                BigDecimal amount = rs.getBigDecimal(2);
                if (ts == null || amount == null) {
                    return null;
                }
                String canal = rs.getString(3);
                String devise = rs.getString(4);
                return new Flux(ts.toLocalDateTime(), amount, canal == null ? "" : canal, blank(devise));
            }).stream().filter(ligne -> ligne != null).toList();
        } catch (BadSqlGrammarException ex) {
            log.info("Flux financier CNN ignoré : {}", root(ex));
            return null;
        }
    }

    private List<Map<String, Object>> canaux(Map<String, List<Flux>> parDevise) {
        Map<String, BigDecimal> sommes = new LinkedHashMap<>();
        Map<String, String> devises = new LinkedHashMap<>();
        parDevise.forEach((key, lignes) -> {
            for (Flux ligne : lignes) {
                String id = ligne.canal() + "\u0000" + key;
                sommes.merge(id, ligne.amount(), BigDecimal::add);
                devises.put(id, key);
            }
        });
        return sommes.entrySet().stream().map(entry -> {
            String key = devises.get(entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canal", entry.getKey().split("\u0000", 2)[0]);
            row.put("devise", key == null || key.isEmpty() ? null : key);
            row.put("deviseStockee", key != null && !key.isEmpty());
            row.put("montant", entry.getValue());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> remboursements() {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            cnn.query("SELECT status, COUNT(*) FROM refunds GROUP BY status", rs -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("statut", rs.getString(1));
                row.put("nombre", rs.getLong(2));
                row.put("montant", null);
                rows.add(row);
            });
            return rows;
        } catch (BadSqlGrammarException ex) {
            log.info("Remboursements CNN ignorés : {}", root(ex));
            return null;
        }
    }

    private Map<String, Object> volumeAffaires() {
        try {
            List<BigDecimal> prix = cnn.query("""
                    SELECT estimated_price FROM orders WHERE order_status = 'DELIVERED'
                    """, (rs, i) -> rs.getBigDecimal(1));
            BigDecimal somme = BigDecimal.ZERO;
            long avecMontant = 0;
            for (BigDecimal prixUn : prix) {
                if (prixUn != null) {
                    somme = somme.add(prixUn);
                    avecMontant++;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("montant", somme);
            row.put("courses", (long) prix.size());
            row.put("coursesAvecMontant", avecMontant);
            row.put("devise", null);
            row.put("deviseStockee", false);
            return row;
        } catch (BadSqlGrammarException ex) {
            log.info("Volume d'affaires CNN ignoré : {}", root(ex));
            return null;
        }
    }

    private static String deviseKey(String devise) {
        return devise == null ? "" : devise;
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Flux(LocalDateTime at, BigDecimal amount, String canal, String devise) {
    }

    private String maxDate(String sql) {
        try {
            Timestamp at = cnn.queryForObject(sql, Timestamp.class);
            return at == null ? null : at.toLocalDateTime().toLocalDate().toString();
        } catch (BadSqlGrammarException | org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> repartition(CnnSnapshot snap) {
        return snap.parStatut().stream().map(row -> {
            Map<String, Object> slice = new LinkedHashMap<>();
            String code = String.valueOf(row.get("statut"));
            slice.put("label", row.get("libelle") == null ? label(code) : row.get("libelle"));
            slice.put("valeur", row.get("volume"));
            slice.put("couleur", row.get("couleur") == null ? "#64748b" : row.get("couleur"));
            return slice;
        }).toList();
    }

    private String livraisonJourMessage(CnnSnapshot snap) {
        if (!snap.available() || snap.coursesLivreesJour() == null || snap.coursesLivreesJour() > 0 || snap.derniereLivraison() == null) {
            return null;
        }
        return "Aucune livraison aujourd'hui — dernière livraison le " + formatFr(snap.derniereLivraison());
    }

    private Long age(CnnSnapshot snap) {
        if (snap.calculeLe() == null) {
            return null;
        }
        return Math.max(0, Duration.between(snap.calculeLe(), Instant.now()).toSeconds());
    }

    private static String label(String code) {
        if (code == null) {
            return "Inconnu";
        }
        return STATUT_LABELS.getOrDefault(code, code);
    }

    private static String later(String left, String right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static String formatFr(String iso) {
        try {
            return LocalDate.parse(iso).format(FR);
        } catch (RuntimeException ex) {
            return iso;
        }
    }

    private static String weekday(java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Lun";
            case TUESDAY -> "Mar";
            case WEDNESDAY -> "Mer";
            case THURSDAY -> "Jeu";
            case FRIDAY -> "Ven";
            case SATURDAY -> "Sam";
            case SUNDAY -> "Dim";
        };
    }

    private record Cache(CnnSnapshot value, Instant expiresAt) {
    }

    private record CountSum(long count, BigDecimal sum) {
    }

    private static final class Draft {
        private Long coursesTotal;
        private Long coursesCreeesJour;
        private Long coursesLivreesJour;
        private Long coursesLivreesPeriode;
        private Long coursesLivreesTotal;
        private Long coursesAnnulees;
        private List<Map<String, Object>> parStatut = List.of();
        private List<Map<String, Object>> volume30j = List.of();
        private String volumeMessage;
        private String derniereCourse;
        private Long coursiersActifs;
        private Long motardsAttente;
        private Long agentsActifs;
        private Long abonnementsRegles;
        private BigDecimal abonnementsMontant;
        private Long abonnementsExpires;
        private Long incidentsOuverts;
        private Long urgences;
        private List<Map<String, Object>> incidents = List.of();
        private String derniereLivraison;
        private List<Map<String, Object>> activite7j = List.of();
        private String activiteMessage;
        private List<Map<String, Object>> delaiBins = List.of();
        private boolean delaiDisponible;
        private String delaiMessage;
        private BigDecimal recettesMois;
        private List<Map<String, Object>> encaissements6m = List.of();
        private List<Map<String, Object>> parCanal = List.of();
        private String dernierReglement;
        private List<Map<String, Object>> recettesParDevise;
        private List<Map<String, Object>> serie6m = List.of();
        private List<Map<String, Object>> remboursements;
        private Map<String, Object> volumeAffaires;
        private List<Map<String, Object>> coursesParMois = List.of();

        private CnnSnapshot build() {
            return new CnnSnapshot(
                    true,
                    "CNN_DB",
                    null,
                    coursesCreeesJour,
                    coursesLivreesJour,
                    coursesLivreesPeriode,
                    coursesLivreesTotal,
                    coursesTotal,
                    coursesAnnulees,
                    parStatut,
                    volume30j,
                    volumeMessage,
                    coursiersActifs,
                    null,
                    COURSIERS_EXPIRES_MESSAGE,
                    abonnementsRegles,
                    abonnementsMontant,
                    abonnementsExpires,
                    incidentsOuverts,
                    urgences,
                    derniereCourse,
                    incidents,
                    motardsAttente,
                    agentsActifs,
                    derniereLivraison,
                    activite7j,
                    activiteMessage,
                    delaiBins,
                    delaiDisponible,
                    delaiMessage,
                    recettesMois,
                    encaissements6m,
                    parCanal,
                    dernierReglement,
                    Instant.now(),
                    recettesParDevise,
                    serie6m,
                    remboursements,
                    volumeAffaires,
                    coursesParMois
            );
        }
    }
}
