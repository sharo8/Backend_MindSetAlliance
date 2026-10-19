package com.mindsetalliance.core.cnn.synthese;

import com.mindsetalliance.core.cnn.CnnReadOnlyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Statistiques Colis na Nga sur une période (7, 30, 90 ou 365 jours), lues en direct dans sa base.
 * Les regroupements par jour, heure et jour de semaine se font à l'heure de Kinshasa.
 * Chaque bloc est indépendant : un bloc illisible vaut null sans bloquer les autres.
 */
@Service
public class CnnStatistiquesService {

    private static final Logger log = LoggerFactory.getLogger(CnnStatistiquesService.class);
    private static final ZoneId KINSHASA = CnnLectureService.KINSHASA;

    private final CnnLectureService lecture;

    public CnnStatistiquesService(CnnLectureService lecture) {
        this.lecture = lecture;
    }

    public boolean disponible() {
        return lecture.disponible();
    }

    private record Periode(int jours, LocalDate premierJour, LocalDate dernierJour, boolean parMois,
                           LocalDateTime debut, LocalDateTime fin, LocalDateTime debutPrecedent) {}

    private record Commande(LocalDateTime creee, String statut, String coursier, String paiement, String colis,
                            BigDecimal prix, BigDecimal distance, String agentId, String motardId) {}

    /** Résultats gardés 60 s par période : changer d'onglet ou revenir sur la page est instantané. */
    private static final java.time.Duration TTL = java.time.Duration.ofSeconds(60);
    private final Map<Integer, Map<String, Object>> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, java.time.Instant> cachedAt = new java.util.concurrent.ConcurrentHashMap<>();

    public Map<String, Object> statistiques(int joursDemandes) {
        int jours = switch (joursDemandes) {
            case 7, 30, 90, 365 -> joursDemandes;
            default -> 30;
        };
        Map<String, Object> hit = cache.get(jours);
        java.time.Instant at = cachedAt.get(jours);
        if (hit != null && at != null && at.plus(TTL).isAfter(java.time.Instant.now())) {
            return hit;
        }
        synchronized (this) {
            hit = cache.get(jours);
            at = cachedAt.get(jours);
            if (hit != null && at != null && at.plus(TTL).isAfter(java.time.Instant.now())) {
                return hit;
            }
            Map<String, Object> fresh = calculer(jours);
            cache.put(jours, fresh);
            cachedAt.put(jours, java.time.Instant.now());
            return fresh;
        }
    }

    private Map<String, Object> calculer(int jours) {
        Periode p = periode(jours);
        CnnReadOnlyClient db = lecture.db();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("calculeLe", OffsetDateTime.now(KINSHASA).toString());
        body.put("periode", Map.of("jours", jours, "debut", p.premierJour().toString(), "fin", p.dernierJour().toString(),
                "granularite", p.parMois() ? "mois" : "jour"));

        // Lectures indépendantes lancées en parallèle (chaque aller-retour passe par le Cloud SQL Auth Proxy).
        java.util.concurrent.CompletableFuture<List<Commande>> cmdFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> safe(() -> commandes(db, p.debutPrecedent(), p.fin())));
        Map<String, Object> autres = new com.mindsetalliance.core.cnn.CnnParallel()
                .add("compteurs", () -> compteurs(db, p.debut(), p.fin()))
                .add("compteursPrecedents", () -> compteurs(db, p.debutPrecedent(), p.debut()))
                .add("inscriptions", () -> inscriptions(db, p))
                .add("credits", () -> credits(db, p))
                .add("abonnements", () -> abonnements(db, p))
                .add("notes", () -> notes(db, p))
                .add("topAssociations", () -> topAssociations(db, p))
                .add("signalements", () -> groupe(db, "SELECT category AS cle, COUNT(*) AS n FROM motard_reports "
                        + "WHERE created_at >= ? AND created_at < ? GROUP BY category ORDER BY n DESC", p))
                .add("urgences", () -> groupe(db, "SELECT caller_role AS cle, COUNT(*) AS n FROM emergency_calls "
                        + "WHERE called_at >= ? AND called_at < ? GROUP BY caller_role ORDER BY n DESC", p))
                .add("remboursements", () -> groupe(db, "SELECT status AS cle, COUNT(*) AS n FROM refunds "
                        + "WHERE requested_at >= ? AND requested_at < ? GROUP BY status ORDER BY n DESC", p))
                .into(new LinkedHashMap<>());
        List<Commande> commandes;
        try {
            commandes = cmdFuture.get(25, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            commandes = null;
        }
        List<Commande> actuelles = commandes == null ? null
                : commandes.stream().filter(c -> !c.creee().isBefore(p.debut())).toList();
        List<Commande> precedentes = commandes == null ? null
                : commandes.stream().filter(c -> c.creee().isBefore(p.debut())).toList();

        body.put("kpis", kpis(actuelles, castMap(autres.get("compteurs"))));
        body.put("kpisPrecedents", kpis(precedentes, castMap(autres.get("compteursPrecedents"))));
        body.put("serie", actuelles == null ? null : serie(p, actuelles));
        body.put("parStatut", actuelles == null ? null : compte(actuelles, Commande::statut));
        body.put("parCoursier", actuelles == null ? null : compte(actuelles, Commande::coursier));
        body.put("parPaiement", actuelles == null ? null : compte(actuelles, Commande::paiement));
        body.put("parTypeColis", actuelles == null ? null : compte(actuelles, Commande::colis));
        body.put("parHeure", actuelles == null ? null : parHeure(actuelles));
        body.put("parJourSemaine", actuelles == null ? null : parJourSemaine(actuelles));
        body.put("inscriptions", autres.get("inscriptions"));
        body.put("credits", autres.get("credits"));
        body.put("abonnements", autres.get("abonnements"));
        body.put("notes", autres.get("notes"));
        body.put("topLivreurs", safe(() -> top(db, actuelles, true)));
        body.put("topMotards", safe(() -> top(db, actuelles, false)));
        body.put("topAssociations", autres.get("topAssociations"));
        body.put("signalements", autres.get("signalements"));
        body.put("urgences", autres.get("urgences"));
        body.put("remboursements", autres.get("remboursements"));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    // ── Période ────────────────────────────────────────────────────────────────

    private Periode periode(int jours) {
        LocalDate aujourdhui = LocalDate.now(KINSHASA);
        boolean parMois = jours > 90;
        LocalDate premier = aujourdhui.minusDays(jours - 1L);
        if (parMois) {
            premier = aujourdhui.withDayOfMonth(1).minusMonths(11);
        }
        long longueur = java.time.temporal.ChronoUnit.DAYS.between(premier, aujourdhui.plusDays(1));
        LocalDateTime debut = stockage(premier.atStartOfDay(KINSHASA));
        LocalDateTime fin = stockage(aujourdhui.plusDays(1).atStartOfDay(KINSHASA));
        LocalDateTime debutPrecedent = stockage(premier.minusDays(longueur).atStartOfDay(KINSHASA));
        return new Periode(jours, premier, aujourdhui, parMois, debut, fin, debutPrecedent);
    }

    private LocalDateTime stockage(ZonedDateTime kinshasa) {
        return kinshasa.withZoneSameInstant(lecture.storageZone()).toLocalDateTime();
    }

    private LocalDateTime kinshasa(LocalDateTime stored) {
        return stored.atZone(lecture.storageZone()).withZoneSameInstant(KINSHASA).toLocalDateTime();
    }

    private String cle(Periode p, LocalDateTime stored) {
        LocalDate d = kinshasa(stored).toLocalDate();
        return p.parMois() ? d.withDayOfMonth(1).toString() : d.toString();
    }

    private Map<String, Map<String, Object>> buckets(Periode p) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        LocalDate d = p.premierJour();
        while (!d.isAfter(p.dernierJour())) {
            String k = d.toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", k);
            out.put(k, m);
            d = p.parMois() ? d.plusMonths(1) : d.plusDays(1);
        }
        return out;
    }

    // ── Commandes ──────────────────────────────────────────────────────────────

    private List<Commande> commandes(CnnReadOnlyClient db, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT o.created_at, o.order_status, o.courier_type, o.payment_mode, o.parcel_type, "
                + "o.estimated_price, o.estimated_distance_km, " + lecture.uid("o.delivery_agent_id") + " AS agent_id, "
                + lecture.uid("o.partner_motard_id") + " AS motard_id FROM orders o WHERE o.created_at >= ? AND o.created_at < ?";
        return db.query(sql, (rs, i) -> new Commande(rs.getObject("created_at", LocalDateTime.class), rs.getString("order_status"),
                rs.getString("courier_type"), rs.getString("payment_mode"), rs.getString("parcel_type"),
                rs.getBigDecimal("estimated_price"), rs.getBigDecimal("estimated_distance_km"),
                rs.getString("agent_id"), rs.getString("motard_id")), from, to);
    }

    /** Compteurs hors commandes, en une seule requête (un aller-retour). */
    private Map<String, Object> compteurs(CnnReadOnlyClient db, LocalDateTime from, LocalDateTime to) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM clients c JOIN users u ON u.id = c.user_id
                     WHERE u.phone NOT LIKE 'DELETED%' AND u.created_at >= ? AND u.created_at < ?) AS nouveaux_clients,
                  (SELECT COUNT(*) FROM partner_motards m JOIN users u ON u.id = m.user_id
                     WHERE u.phone NOT LIKE 'DELETED%' AND u.created_at >= ? AND u.created_at < ?) AS nouveaux_motards,
                  (SELECT COALESCE(SUM(credits_purchased), 0) FROM motard_credit_subscriptions
                     WHERE is_free_starter = FALSE AND purchased_at >= ? AND purchased_at < ?) AS credits_vendus,
                  (SELECT COALESCE(SUM(amount_paid), 0) FROM motard_credit_subscriptions
                     WHERE is_free_starter = FALSE AND purchased_at >= ? AND purchased_at < ?) AS revenus_credits,
                  (SELECT COALESCE(SUM(amount), 0) FROM subscription_payments WHERE status = 'COMPLETED'
                     AND COALESCE(completed_at, created_at) >= ? AND COALESCE(completed_at, created_at) < ?) AS revenus_abonnements,
                  (SELECT AVG(score) FROM ratings WHERE created_at >= ? AND created_at < ?) AS note_moyenne,
                  (SELECT COUNT(*) FROM emergency_calls WHERE called_at >= ? AND called_at < ?) AS urgences,
                  (SELECT COUNT(*) FROM motard_reports WHERE created_at >= ? AND created_at < ?) AS signalements""";
        Object[] args = new Object[16];
        for (int i = 0; i < 16; i += 2) {
            args[i] = from;
            args[i + 1] = to;
        }
        return db.query(sql, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            if (rs.next()) {
                m.put("nouveauxClients", rs.getLong("nouveaux_clients"));
                m.put("nouveauxMotards", rs.getLong("nouveaux_motards"));
                m.put("creditsVendus", rs.getLong("credits_vendus"));
                m.put("revenusCredits", rs.getBigDecimal("revenus_credits"));
                m.put("revenusAbonnements", rs.getBigDecimal("revenus_abonnements"));
                BigDecimal avg = rs.getBigDecimal("note_moyenne");
                m.put("noteMoyenne", avg == null ? null : avg.setScale(2, RoundingMode.HALF_UP));
                m.put("urgences", rs.getLong("urgences"));
                m.put("signalements", rs.getLong("signalements"));
            }
            return m;
        }, args);
    }

    private Map<String, Object> kpis(List<Commande> cmds, Map<String, Object> compteurs) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (cmds != null) {
            long total = cmds.size();
            long livrees = cmds.stream().filter(c -> "DELIVERED".equals(c.statut())).count();
            long annulees = cmds.stream().filter(c -> "CANCELLED".equals(c.statut())).count();
            BigDecimal chiffre = cmds.stream().filter(c -> "DELIVERED".equals(c.statut()) && c.prix() != null)
                    .map(Commande::prix).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<BigDecimal> distances = cmds.stream().map(Commande::distance).filter(d -> d != null && d.signum() > 0).toList();
            m.put("commandes", total);
            m.put("livrees", livrees);
            m.put("annulees", annulees);
            m.put("tauxLivraison", total == 0 ? null : Math.round(livrees * 1000.0 / total) / 10.0);
            m.put("chiffreLivre", chiffre);
            m.put("panierMoyen", livrees == 0 ? null : chiffre.divide(BigDecimal.valueOf(livrees), 0, RoundingMode.HALF_UP));
            m.put("distanceMoyenne", distances.isEmpty() ? null
                    : distances.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(distances.size()), 1, RoundingMode.HALF_UP));
        }
        for (String k : List.of("nouveauxClients", "nouveauxMotards", "creditsVendus", "revenusCredits", "revenusAbonnements",
                "noteMoyenne", "urgences", "signalements")) {
            m.put(k, compteurs == null ? null : compteurs.get(k));
        }
        return m;
    }

    private List<Map<String, Object>> serie(Periode p, List<Commande> cmds) {
        Map<String, Map<String, Object>> out = buckets(p);
        out.values().forEach(m -> {
            m.put("creees", 0L);
            m.put("livrees", 0L);
            m.put("annulees", 0L);
            m.put("chiffre", BigDecimal.ZERO);
        });
        for (Commande c : cmds) {
            Map<String, Object> m = out.get(cle(p, c.creee()));
            if (m == null) continue;
            m.merge("creees", 1L, (a, b) -> (Long) a + (Long) b);
            if ("DELIVERED".equals(c.statut())) {
                m.merge("livrees", 1L, (a, b) -> (Long) a + (Long) b);
                if (c.prix() != null) m.merge("chiffre", c.prix(), (a, b) -> ((BigDecimal) a).add((BigDecimal) b));
            } else if ("CANCELLED".equals(c.statut())) {
                m.merge("annulees", 1L, (a, b) -> (Long) a + (Long) b);
            }
        }
        return new ArrayList<>(out.values());
    }

    private List<Map<String, Object>> compte(List<Commande> cmds, java.util.function.Function<Commande, String> key) {
        Map<String, Long> counts = new TreeMap<>();
        for (Commande c : cmds) {
            String k = key.apply(c);
            counts.merge(k == null ? "INCONNU" : k, 1L, Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> out.add(Map.of("cle", e.getKey(), "valeur", e.getValue())));
        return out;
    }

    private List<Map<String, Object>> parHeure(List<Commande> cmds) {
        long[] h = new long[24];
        for (Commande c : cmds) h[kinshasa(c.creee()).getHour()]++;
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < 24; i++) out.add(Map.of("heure", i, "valeur", h[i]));
        return out;
    }

    private List<Map<String, Object>> parJourSemaine(List<Commande> cmds) {
        long[] d = new long[7];
        for (Commande c : cmds) d[kinshasa(c.creee()).getDayOfWeek().getValue() - 1]++;
        List<Map<String, Object>> out = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) out.add(Map.of("jour", day.getValue(), "valeur", d[day.getValue() - 1]));
        return out;
    }

    // ── Autres domaines ────────────────────────────────────────────────────────

    private List<Map<String, Object>> inscriptions(CnnReadOnlyClient db, Periode p) {
        Map<String, Map<String, Object>> out = buckets(p);
        out.values().forEach(m -> {
            m.put("CLIENT", 0L);
            m.put("DELIVERY_AGENT", 0L);
            m.put("PARTNER_MOTARD", 0L);
            m.put("ASSOCIATION", 0L);
        });
        // Même définition que le tableau de bord : comptes réellement présents dans chaque table, hors comptes supprimés.
        String sql = """
                SELECT u.created_at, 'CLIENT' AS role FROM clients c JOIN users u ON u.id = c.user_id
                  WHERE u.phone NOT LIKE 'DELETED%' AND u.created_at >= ? AND u.created_at < ?
                UNION ALL
                SELECT u.created_at, 'PARTNER_MOTARD' FROM partner_motards m JOIN users u ON u.id = m.user_id
                  WHERE u.phone NOT LIKE 'DELETED%' AND u.created_at >= ? AND u.created_at < ?
                UNION ALL
                SELECT u.created_at, 'DELIVERY_AGENT' FROM delivery_agents d JOIN users u ON u.id = d.user_id
                  WHERE u.phone NOT LIKE 'DELETED%' AND u.created_at >= ? AND u.created_at < ?
                UNION ALL
                SELECT u.created_at, 'ASSOCIATION' FROM partner_associations a JOIN users u ON u.id = a.id
                  WHERE u.phone NOT LIKE 'DELETED%' AND u.created_at >= ? AND u.created_at < ?""";
        db.query(sql,
                (rs, i) -> {
                    LocalDateTime at = rs.getObject("created_at", LocalDateTime.class);
                    String role = rs.getString("role");
                    Map<String, Object> m = at == null ? null : out.get(cle(p, at));
                    if (m != null && m.containsKey(role)) m.merge(role, 1L, (a, b) -> (Long) a + (Long) b);
                    return 0;
                }, p.debut(), p.fin(), p.debut(), p.fin(), p.debut(), p.fin(), p.debut(), p.fin());
        return new ArrayList<>(out.values());
    }

    private List<Map<String, Object>> credits(CnnReadOnlyClient db, Periode p) {
        Map<String, Map<String, Object>> out = buckets(p);
        out.values().forEach(m -> {
            m.put("credits", 0L);
            m.put("offerts", 0L);
            m.put("montant", BigDecimal.ZERO);
        });
        db.query("SELECT purchased_at, credits_purchased, amount_paid, is_free_starter FROM motard_credit_subscriptions "
                + "WHERE purchased_at >= ? AND purchased_at < ?", (rs, i) -> {
            LocalDateTime at = rs.getObject("purchased_at", LocalDateTime.class);
            Map<String, Object> m = at == null ? null : out.get(cle(p, at));
            if (m != null) {
                long n = rs.getLong("credits_purchased");
                if (Boolean.TRUE.equals(CnnLectureService.bool(rs, "is_free_starter"))) {
                    m.merge("offerts", n, (a, b) -> (Long) a + (Long) b);
                } else {
                    m.merge("credits", n, (a, b) -> (Long) a + (Long) b);
                    BigDecimal amount = rs.getBigDecimal("amount_paid");
                    if (amount != null) m.merge("montant", amount, (a, b) -> ((BigDecimal) a).add((BigDecimal) b));
                }
            }
            return 0;
        }, p.debut(), p.fin());
        return new ArrayList<>(out.values());
    }

    private List<Map<String, Object>> abonnements(CnnReadOnlyClient db, Periode p) {
        Map<String, Map<String, Object>> out = buckets(p);
        out.values().forEach(m -> {
            m.put("paiements", 0L);
            m.put("montant", BigDecimal.ZERO);
        });
        db.query("SELECT COALESCE(completed_at, created_at) AS at, amount FROM subscription_payments "
                + "WHERE status = 'COMPLETED' AND COALESCE(completed_at, created_at) >= ? AND COALESCE(completed_at, created_at) < ?", (rs, i) -> {
            LocalDateTime at = rs.getObject("at", LocalDateTime.class);
            Map<String, Object> m = at == null ? null : out.get(cle(p, at));
            if (m != null) {
                m.merge("paiements", 1L, (a, b) -> (Long) a + (Long) b);
                BigDecimal amount = rs.getBigDecimal("amount");
                if (amount != null) m.merge("montant", amount, (a, b) -> ((BigDecimal) a).add((BigDecimal) b));
            }
            return 0;
        }, p.debut(), p.fin());
        return new ArrayList<>(out.values());
    }

    private List<Map<String, Object>> notes(CnnReadOnlyClient db, Periode p) {
        long[] s = new long[5];
        db.query("SELECT score, COUNT(*) AS n FROM ratings WHERE created_at >= ? AND created_at < ? GROUP BY score", (rs, i) -> {
            int score = rs.getInt("score");
            if (score >= 1 && score <= 5) s[score - 1] += rs.getLong("n");
            return 0;
        }, p.debut(), p.fin());
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 5; i++) out.add(Map.of("note", i, "valeur", s[i - 1]));
        return out;
    }

    /** Coursiers ayant le plus livré sur la période (livreurs de l'entreprise ou motards partenaires). */
    private List<Map<String, Object>> top(CnnReadOnlyClient db, List<Commande> cmds, boolean livreurs) {
        if (cmds == null) return null;
        Map<String, long[]> parCoursier = new LinkedHashMap<>();
        for (Commande c : cmds) {
            String id = livreurs ? c.agentId() : c.motardId();
            if (id == null) continue;
            long[] v = parCoursier.computeIfAbsent(id, k -> new long[2]);
            v[0]++;
            if ("DELIVERED".equals(c.statut())) v[1]++;
        }
        List<Map.Entry<String, long[]>> best = parCoursier.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[1]).reversed()
                        .thenComparing(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed()))
                .limit(8).toList();
        String table = livreurs ? "delivery_agents" : "partner_motards";
        Map<String, String> noms = new java.util.HashMap<>();
        if (!best.isEmpty()) {
            String marks = String.join(", ", java.util.Collections.nCopies(best.size(), lecture.bin("?")));
            db.query("SELECT " + lecture.uid("user_id") + " AS id, CONCAT_WS(' ', first_name, last_name) AS nom FROM " + table
                    + " WHERE user_id IN (" + marks + ")", (rs, i) -> noms.put(rs.getString("id"), rs.getString("nom")),
                    best.stream().map(Map.Entry::getKey).toArray());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : best) {
            String nom = noms.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getKey());
            m.put("nom", nom == null || nom.isBlank() ? null : nom);
            m.put("courses", e.getValue()[0]);
            m.put("livrees", e.getValue()[1]);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> topAssociations(CnnReadOnlyClient db, Periode p) {
        return db.query("""
                SELECT a.name, COUNT(*) AS courses, SUM(CASE WHEN o.order_status = 'DELIVERED' THEN 1 ELSE 0 END) AS livrees
                FROM orders o
                JOIN partner_motards m ON m.user_id = o.partner_motard_id
                JOIN partner_associations a ON a.id = m.association_id
                WHERE o.created_at >= ? AND o.created_at < ?
                GROUP BY a.id, a.name
                ORDER BY livrees DESC, courses DESC
                LIMIT 8""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nom", rs.getString("name"));
            m.put("courses", rs.getLong("courses"));
            m.put("livrees", rs.getLong("livrees"));
            return m;
        }, p.debut(), p.fin());
    }

    private List<Map<String, Object>> groupe(CnnReadOnlyClient db, String sql, Periode p) {
        return db.query(sql, (rs, i) -> Map.<String, Object>of("cle", String.valueOf(rs.getString("cle")), "valeur", rs.getLong("n")),
                p.debut(), p.fin());
    }

    // ── Outils ─────────────────────────────────────────────────────────────────

    private <T> T safe(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Statistiques Colis na Nga : bloc illisible : {}", e.getMessage());
            return null;
        }
    }
}
