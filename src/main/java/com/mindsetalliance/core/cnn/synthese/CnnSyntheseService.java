package com.mindsetalliance.core.cnn.synthese;

import com.mindsetalliance.core.cnn.CnnProperties;
import com.mindsetalliance.core.cnn.CnnReadOnlyClient;
import com.mindsetalliance.core.cnn.CnnSourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Synthèse complète de Colis na Nga, lue en direct dans sa base (lecture seule).
 *
 * Les règles de calcul reprennent exactement celles du tableau de bord de l'application
 * d'administration Colis na Nga (AdminService.dashboard, statsRevenue, statsOrders et
 * l'écran d'accueil admin) : comptes supprimés exclus (téléphone « DELETED… »),
 * commandes archivées exclues des compteurs du jour, etc.
 *
 * Aucune valeur n'est inventée : un indicateur qui ne peut pas être lu vaut null
 * et l'interface affiche « donnée indisponible ».
 */
@Service
public class CnnSyntheseService {

    private static final Logger log = LoggerFactory.getLogger(CnnSyntheseService.class);
    static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    /** Statuts « en cours » : mêmes que l'écran d'accueil admin Colis na Nga. */
    static final List<String> EN_COURS = List.of("LIVREUR_EN_ROUTE", "AGENT_EN_ROUTE", "COURIER_AT_PICKUP",
            "AWAITING_PICKUP_PAYMENT", "COLLECTED", "IN_TRANSIT", "AWAITING_DELIVERY_PAYMENT", "PENDING_MOTARD_ACCEPTANCE");

    private final CnnProperties properties;
    private final CnnReadOnlyClient db;
    private final CnnSourceStatus sourceStatus;
    private final CnnExchangeRates exchangeRates;
    private final Object lock = new Object();
    private volatile Map<String, Object> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public CnnSyntheseService(CnnProperties properties,
                              @Qualifier("cnnJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc,
                              ObjectProvider<CnnSourceStatus> sourceStatus,
                              CnnExchangeRates exchangeRates) {
        this.properties = properties;
        JdbcTemplate template = jdbc.getIfAvailable();
        this.db = template == null ? null : new CnnReadOnlyClient(template);
        this.sourceStatus = sourceStatus.getIfAvailable();
        this.exchangeRates = exchangeRates;
    }

    public Map<String, Object> synthese() {
        Duration ttl = Duration.ofSeconds(Math.max(5, properties.getCacheSeconds()));
        Map<String, Object> hit = cached;
        if (hit != null && cachedAt.plus(ttl).isAfter(Instant.now())) {
            return hit;
        }
        synchronized (lock) {
            hit = cached;
            if (hit != null && cachedAt.plus(ttl).isAfter(Instant.now())) {
                return hit;
            }
            Map<String, Object> fresh = load();
            if (Boolean.TRUE.equals(fresh.get("disponible"))) {
                cached = fresh;
                cachedAt = Instant.now();
            }
            return fresh;
        }
    }

    // ── Chargement ─────────────────────────────────────────────────────────────

    private Map<String, Object> load() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("calculeLe", OffsetDateTime.now(KINSHASA).toString());
        body.put("readOnlyVerified", sourceStatus != null && sourceStatus.readOnlyVerified());
        body.put("tauxChange", exchangeRates.current());
        if (db == null) {
            body.put("disponible", false);
            body.put("message", "Source Colis na Nga non configurée (MA_CNN_DB_URL).");
            return body;
        }
        try {
            db.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            log.warn("Base Colis na Nga injoignable : {}", e.getMessage());
            body.put("disponible", false);
            body.put("message", "Base Colis na Nga injoignable. Vérifiez la connexion (Cloud SQL Auth Proxy).");
            return body;
        }

        Bounds b = bounds();
        String clientFk = firstColumn("orders", "client_id", "client_user_id");

        body.put("disponible", true);
        body.put("message", null);
        // Blocs indépendants lus en parallèle (chaque aller-retour passe par le Cloud SQL Auth Proxy).
        new com.mindsetalliance.core.cnn.CnnParallel()
                .add("commandes", () -> commandes(b))
                .add("parStatut", () -> rows("SELECT order_status AS statut, COUNT(*) AS volume FROM orders GROUP BY order_status ORDER BY volume DESC",
                        rs -> map("statut", rs.getString("statut"), "volume", rs.getLong("volume"))))
                .add("volume30j", () -> volume30j(b))
                .add("livreurs", this::livreurs)
                .add("motards", this::motards)
                .add("clients", this::clients)
                .add("abonnements", this::abonnements)
                .add("revenus", () -> revenus(b))
                .add("mobileMoney", this::mobileMoney)
                .add("creditsMotards", this::creditsMotards)
                .add("support", () -> support(b))
                .add("associations", this::associations)
                .add("adminsActifs", () -> count("""
                        SELECT COUNT(*) FROM administrators a JOIN users u ON u.id = a.user_id
                        WHERE u.role = 'ADMIN' AND a.active = TRUE AND u.phone NOT LIKE 'DELETED%'"""))
                .add("notes", this::notes)
                .add("enService", () -> enService(b))
                .add("dernieresCommandes", () -> dernieresCommandes(b, clientFk))
                .into(body);
        return body;
    }

    private Map<String, Object> commandes(Bounds b) {
        String enCours = inList(EN_COURS);
        // Une seule requête (un aller-retour) pour tous les compteurs de commandes.
        String last = "COALESCE(updated_at, created_at)";
        Map<String, Object> agg = db.query("SELECT COUNT(*) AS total,"
                + " SUM(order_status = 'DELIVERED') AS livrees,"
                + " SUM(order_status = 'CANCELLED') AS annulees,"
                + " SUM(archived = FALSE AND order_status = 'PENDING') AS en_attente,"
                + " SUM(archived = FALSE AND order_status IN " + enCours + ") AS en_cours,"
                + " SUM(archived = FALSE AND order_status = 'DELIVERED' AND " + last + " >= ? AND " + last + " < ?) AS livrees_jour,"
                + " SUM(created_at >= ?) AS creees_jour,"
                + " SUM(created_at >= ?) AS creees_mois,"
                + " SUM(is_secret = TRUE) AS secretes,"
                + " SUM(parcel_type = 'SENSITIVE') AS sensibles,"
                + " SUM(archived = TRUE) AS archivees,"
                + " SUM(courier_type = 'COMPANY_AGENT') AS par_livreur,"
                + " SUM(courier_type = 'PARTNER_MOTARD') AS par_motard,"
                + " COALESCE(SUM(CASE WHEN order_status = 'DELIVERED' THEN estimated_price END), 0) AS chiffre"
                + " FROM orders", rs -> {
            Map<String, Object> r = new LinkedHashMap<>();
            if (rs.next()) {
                for (String c : List.of("total", "livrees", "annulees", "en_attente", "en_cours", "livrees_jour", "creees_jour",
                        "creees_mois", "secretes", "sensibles", "archivees", "par_livreur", "par_motard")) {
                    r.put(c, rs.getLong(c));
                }
                r.put("chiffre", rs.getBigDecimal("chiffre"));
            }
            return r;
        }, b.startToday, b.startTomorrow, b.startToday, b.startMonth);
        long total = (Long) agg.getOrDefault("total", 0L);
        long livrees = (Long) agg.getOrDefault("livrees", 0L);
        long annulees = (Long) agg.getOrDefault("annulees", 0L);
        Map<String, Object> m = new LinkedHashMap<>();
        // Cartes de l'accueil admin : commandes non archivées.
        m.put("enAttente", agg.get("en_attente"));
        m.put("enCours", agg.get("en_cours"));
        m.put("livreesAujourdhui", agg.get("livrees_jour"));
        // Statistiques (AdminService.statsOrders).
        m.put("total", total);
        m.put("livrees", livrees);
        m.put("annulees", annulees);
        m.put("actives", total - livrees - annulees);
        m.put("creeesAujourdhui", agg.get("creees_jour"));
        m.put("creeesCeMois", agg.get("creees_mois"));
        m.put("tauxReussite", total == 0 ? 0.0 : Math.round(livrees * 1000.0 / total) / 10.0);
        m.put("secretes", agg.get("secretes"));
        m.put("sensibles", agg.get("sensibles"));
        m.put("archivees", agg.get("archivees"));
        m.put("parLivreur", agg.get("par_livreur"));
        m.put("parMotard", agg.get("par_motard"));
        m.put("chiffreLivre", agg.get("chiffre"));
        return m;
    }

    private List<Map<String, Object>> volume30j(Bounds b) {
        Map<LocalDate, Long> perDay = new LinkedHashMap<>();
        LocalDate today = b.today;
        for (int i = 29; i >= 0; i--) {
            perDay.put(today.minusDays(i), 0L);
        }
        List<LocalDateTime> created = safe(() -> db.query("SELECT created_at FROM orders WHERE created_at >= ?",
                (rs, i) -> rs.getObject(1, LocalDateTime.class), b.start30));
        if (created != null) {
            for (LocalDateTime stored : created) {
                if (stored == null) continue;
                perDay.computeIfPresent(toKinshasa(stored).toLocalDate(), (k, v) -> v + 1);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        perDay.forEach((day, n) -> out.add(map("jour", day.toString(), "volume", n)));
        return out;
    }

    private Map<String, Object> livreurs() {
        String live = " FROM delivery_agents d JOIN users u ON u.id = d.user_id WHERE u.phone NOT LIKE 'DELETED%'";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", count("SELECT COUNT(*)" + live));
        // « Livreurs disponibles » : actifs et en service (même règle que la carte de l'accueil admin).
        m.put("enService", count("SELECT COUNT(*)" + live + " AND COALESCE(d.active, TRUE) = TRUE AND d.status = 'ON_DUTY'"));
        m.put("desactives", count("SELECT COUNT(*)" + live + " AND d.active = FALSE"));
        return m;
    }

    private Map<String, Object> motards() {
        String live = " FROM partner_motards m JOIN users u ON u.id = m.user_id WHERE u.phone NOT LIKE 'DELETED%'";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", count("SELECT COUNT(*)" + live));
        m.put("enService", count("""
                SELECT COUNT(*) FROM partner_motards
                WHERE validation_status = 'APPROVED' AND status = 'ON_DUTY' AND active = TRUE"""));
        m.put("aValider", count("SELECT COUNT(*) FROM partner_motards WHERE validation_status = 'ASSOCIATION_VALIDATED'"));
        m.put("attenteAssociation", count("SELECT COUNT(*)" + live + " AND m.validation_status = 'PENDING_VALIDATION'"));
        m.put("approuves", count("SELECT COUNT(*)" + live + " AND m.validation_status = 'APPROVED'"));
        m.put("refuses", count("SELECT COUNT(*)" + live + " AND m.validation_status = 'REJECTED'"));
        m.put("creditsEnCirculation", count("SELECT COALESCE(SUM(m.credit_balance), 0)" + live));
        return m;
    }

    private Map<String, Object> clients() {
        String live = " FROM clients c JOIN users u ON u.id = c.user_id WHERE u.phone NOT LIKE 'DELETED%'";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", count("SELECT COUNT(*)" + live));
        m.put("desactives", count("SELECT COUNT(*)" + live + " AND c.active = FALSE"));
        m.put("entreprises", count("SELECT COUNT(*) FROM subscribed_clients s JOIN users u ON u.id = s.client_id WHERE u.phone NOT LIKE 'DELETED%'"));
        m.put("entreprisesEnAttente", count("SELECT COUNT(*) FROM subscribed_clients s JOIN users u ON u.id = s.client_id WHERE u.phone NOT LIKE 'DELETED%' AND s.status = 'PENDING'"));
        m.put("nouveauxCeMois", count("SELECT COUNT(*)" + live + " AND u.created_at >= ?", bounds().startMonth));
        return m;
    }

    private Map<String, Object> abonnements() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actifs", count("SELECT COUNT(*) FROM subscriptions WHERE status = 'ACTIVE'"));
        m.put("total", count("SELECT COUNT(*) FROM subscriptions"));
        m.put("expirant30j", count("""
                SELECT COUNT(*) FROM subscriptions WHERE status = 'ACTIVE'
                AND expiration_date IS NOT NULL AND expiration_date BETWEEN ? AND ?""", bounds().today, bounds().today.plusDays(30)));
        m.put("facturesParStatut", rows("""
                SELECT status, COUNT(*) AS nombre, COALESCE(SUM(amount), 0) AS montant, COALESCE(currency, '') AS devise
                FROM subscription_invoices GROUP BY status, currency ORDER BY status""",
                rs -> map("statut", rs.getString("status"), "nombre", rs.getLong("nombre"),
                        "montant", rs.getBigDecimal("montant"), "devise", blankToNull(rs.getString("devise")))));
        return m;
    }

    private Map<String, Object> revenus(Bounds b) {
        Map<String, Object> m = new LinkedHashMap<>();
        // AdminService.statsRevenue : paiements confirmés.
        m.put("total", sum("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'CONFIRMED'"));
        m.put("paiements", count("SELECT COUNT(*) FROM payments WHERE status = 'CONFIRMED'"));
        m.put("mois", sum("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'CONFIRMED' AND confirmed_at >= ?", b.startMonth));
        m.put("paiementsMois", count("SELECT COUNT(*) FROM payments WHERE status = 'CONFIRMED' AND confirmed_at >= ?", b.startMonth));
        m.put("jour", sum("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'CONFIRMED' AND confirmed_at >= ?", b.startToday));
        m.put("paiementsJour", count("SELECT COUNT(*) FROM payments WHERE status = 'CONFIRMED' AND confirmed_at >= ?", b.startToday));
        m.put("enAttente", count("SELECT COUNT(*) FROM payments WHERE status = 'PENDING'"));
        m.put("parMode", rows("""
                SELECT mode, COUNT(*) AS nombre, COALESCE(SUM(amount), 0) AS montant
                FROM payments WHERE status = 'CONFIRMED' GROUP BY mode ORDER BY montant DESC""",
                rs -> map("mode", rs.getString("mode"), "nombre", rs.getLong("nombre"), "montant", rs.getBigDecimal("montant"))));
        return m;
    }

    private Map<String, Object> mobileMoney() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reussies", rows("""
                SELECT COALESCE(confirmed_currency, currency) AS devise, COUNT(*) AS nombre,
                       COALESCE(SUM(COALESCE(confirmed_amount, amount)), 0) AS montant
                FROM mobile_money_transactions WHERE status = 'COMPLETED'
                GROUP BY COALESCE(confirmed_currency, currency)""",
                rs -> map("devise", rs.getString("devise"), "nombre", rs.getLong("nombre"), "montant", rs.getBigDecimal("montant"))));
        m.put("enCours", count("SELECT COUNT(*) FROM mobile_money_transactions WHERE status IN ('SUBMITTED', 'ACCEPTED', 'PROCESSING', 'IN_RECONCILIATION')"));
        m.put("echouees", count("SELECT COUNT(*) FROM mobile_money_transactions WHERE status IN ('FAILED', 'REJECTED')"));
        m.put("parObjet", rows("""
                SELECT purpose, COUNT(*) AS nombre FROM mobile_money_transactions
                WHERE status = 'COMPLETED' GROUP BY purpose""",
                rs -> map("objet", rs.getString("purpose"), "nombre", rs.getLong("nombre"))));
        return m;
    }

    private Map<String, Object> creditsMotards() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("achats", count("SELECT COUNT(*) FROM motard_credit_subscriptions WHERE is_free_starter = FALSE"));
        m.put("creditsVendus", count("SELECT COALESCE(SUM(credits_purchased), 0) FROM motard_credit_subscriptions WHERE is_free_starter = FALSE"));
        m.put("montant", sum("SELECT COALESCE(SUM(amount_paid), 0) FROM motard_credit_subscriptions WHERE is_free_starter = FALSE"));
        m.put("partAssociations", sum("SELECT COALESCE(SUM(association_revenue), 0) FROM motard_credit_subscriptions WHERE is_free_starter = FALSE"));
        m.put("creditsOfferts", count("SELECT COALESCE(SUM(credits_purchased), 0) FROM motard_credit_subscriptions WHERE is_free_starter = TRUE"));
        return m;
    }

    private Map<String, Object> support(Bounds b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("remboursements", rows("SELECT status, COUNT(*) AS nombre FROM refunds GROUP BY status",
                rs -> map("statut", rs.getString("status"), "nombre", rs.getLong("nombre"))));
        m.put("remboursementsEnAttente", count("SELECT COUNT(*) FROM refunds WHERE status = 'PENDING'"));
        m.put("appelsUrgence", count("SELECT COUNT(*) FROM emergency_calls"));
        m.put("appelsUrgenceAujourdhui", count("SELECT COUNT(*) FROM emergency_calls WHERE called_at >= ?", b.startToday));
        m.put("signalementsOuverts", count("SELECT COUNT(*) FROM motard_reports WHERE status = 'OPEN'"));
        m.put("suppressionsMotardEnAttente", count("SELECT COUNT(*) FROM motard_deletion_requests WHERE status = 'PENDING'"));
        return m;
    }

    private Map<String, Object> associations() {
        String live = " FROM partner_associations a JOIN users u ON u.id = a.id WHERE u.phone NOT LIKE 'DELETED%'";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", count("SELECT COUNT(*)" + live));
        m.put("actives", count("SELECT COUNT(*)" + live + " AND a.active = TRUE"));
        m.put("revenusCumules", sum("SELECT COALESCE(SUM(a.total_revenue), 0)" + live));
        // AdminService.statsRevenue : top 5 par revenu, avec le nombre de motards.
        m.put("top", rows("""
                SELECT a.id, a.name, a.total_revenue,
                       (SELECT COUNT(*) FROM partner_motards m WHERE m.association_id = a.id) AS motards
                """ + live + " ORDER BY a.total_revenue DESC LIMIT 5",
                rs -> map("nom", rs.getString("name"), "revenus", rs.getBigDecimal("total_revenue"), "motards", rs.getLong("motards"))));
        return m;
    }

    private Map<String, Object> notes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", count("SELECT COUNT(*) FROM ratings"));
        m.put("moyenne", safe(() -> {
            BigDecimal avg = db.queryForObject("SELECT AVG(score) FROM ratings", BigDecimal.class);
            return avg == null ? null : avg.setScale(2, RoundingMode.HALF_UP);
        }));
        return m;
    }

    /** Coursiers en service (livreurs et motards), avec leur dernière position connue. */
    private List<Map<String, Object>> enService(Bounds b) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> agents = rows("""
                SELECT d.first_name, d.last_name, u.phone, d.last_position_at, d.latitude, d.longitude
                FROM delivery_agents d JOIN users u ON u.id = d.user_id
                WHERE u.phone NOT LIKE 'DELETED%' AND COALESCE(d.active, TRUE) = TRUE AND d.status = 'ON_DUTY'
                ORDER BY d.last_position_at DESC""",
                rs -> courier("LIVREUR", rs));
        List<Map<String, Object>> motards = rows("""
                SELECT m.first_name, m.last_name, u.phone, m.last_position_at, m.latitude, m.longitude
                FROM partner_motards m JOIN users u ON u.id = m.user_id
                WHERE m.validation_status = 'APPROVED' AND m.status = 'ON_DUTY' AND m.active = TRUE
                ORDER BY m.last_position_at DESC""",
                rs -> courier("MOTARD", rs));
        if (agents != null) out.addAll(agents);
        if (motards != null) out.addAll(motards);
        return out;
    }

    private Map<String, Object> courier(String type, ResultSet rs) throws SQLException {
        LocalDateTime at = rs.getObject("last_position_at", LocalDateTime.class);
        String name = (nz(rs.getString("first_name")) + " " + nz(rs.getString("last_name"))).trim();
        return map("type", type, "nom", name.isEmpty() ? rs.getString("phone") : name,
                "telephone", rs.getString("phone"),
                "dernierePosition", at == null ? null : toKinshasa(at).toOffsetDateTime().toString(),
                "localise", rs.getObject("latitude") != null && rs.getObject("longitude") != null);
    }

    private List<Map<String, Object>> dernieresCommandes(Bounds b, String clientFk) {
        String client = clientFk == null ? "NULL AS client_prenom, NULL AS client_tel"
                : "(SELECT c.first_name FROM clients c WHERE c.user_id = o." + clientFk + ") AS client_prenom, "
                + "(SELECT u.phone FROM users u WHERE u.id = o." + clientFk + ") AS client_tel";
        return rows("SELECT o.id, o.order_status, o.pickup_address, o.delivery_address, o.estimated_price, o.courier_type, o.created_at, "
                        + client + " FROM orders o WHERE o.archived = FALSE ORDER BY o.created_at DESC LIMIT 10",
                rs -> {
                    LocalDateTime at = rs.getObject("created_at", LocalDateTime.class);
                    return map("id", rs.getString("id"), "statut", rs.getString("order_status"),
                            "depart", rs.getString("pickup_address"), "arrivee", rs.getString("delivery_address"),
                            "prix", rs.getBigDecimal("estimated_price"), "typeCoursier", rs.getString("courier_type"),
                            "client", rs.getString("client_prenom"), "clientTelephone", rs.getString("client_tel"),
                            "creeLe", at == null ? null : toKinshasa(at).toOffsetDateTime().toString());
                });
    }

    // ── Outils ─────────────────────────────────────────────────────────────────

    /** Bornes « aujourd'hui / ce mois » à Kinshasa, exprimées dans le fuseau de stockage de la base. */
    record Bounds(LocalDate today, LocalDateTime startToday, LocalDateTime startTomorrow,
                  LocalDateTime startMonth, LocalDateTime start30) {}

    Bounds bounds() {
        LocalDate today = LocalDate.now(KINSHASA);
        return new Bounds(today,
                toStorage(today.atStartOfDay()),
                toStorage(today.plusDays(1).atStartOfDay()),
                toStorage(today.withDayOfMonth(1).atStartOfDay()),
                toStorage(today.minusDays(29).atStartOfDay()));
    }

    private ZoneId storageZone() {
        try {
            return ZoneId.of(properties.getStorageZone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    LocalDateTime toStorage(LocalDateTime kinshasa) {
        return kinshasa.atZone(KINSHASA).withZoneSameInstant(storageZone()).toLocalDateTime();
    }

    ZonedDateTime toKinshasa(LocalDateTime stored) {
        return stored.atZone(storageZone()).withZoneSameInstant(KINSHASA);
    }

    private String firstColumn(String table, String... candidates) {
        for (String c : candidates) {
            Long n = count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?", table, c);
            if (n != null && n > 0) return c;
        }
        return null;
    }

    private Long count(String sql, Object... args) {
        return safe(() -> {
            Number n = db.queryForObject(sql, Number.class, args);
            return n == null ? 0L : n.longValue();
        });
    }

    private BigDecimal sum(String sql, Object... args) {
        return safe(() -> db.queryForObject(sql, BigDecimal.class, args));
    }

    interface RowFn {
        Map<String, Object> apply(ResultSet rs) throws SQLException;
    }

    private List<Map<String, Object>> rows(String sql, RowFn fn, Object... args) {
        return safe(() -> db.query(sql, (rs, i) -> fn.apply(rs), args));
    }

    /** Un indicateur illisible (table ou colonne absente) vaut null, sans bloquer les autres. */
    private <T> T safe(Supplier<T> query) {
        try {
            return query.get();
        } catch (Exception e) {
            log.warn("Indicateur Colis na Nga illisible : {}", e.getMessage());
            return null;
        }
    }

    private static String inList(List<String> values) {
        return "('" + String.join("', '", values) + "')";
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
