package com.mindsetalliance.core.cnn.synthese;

import com.mindsetalliance.core.cnn.CnnProperties;
import com.mindsetalliance.core.cnn.CnnReadOnlyClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Listes Colis na Nga lues en direct dans sa base (lecture seule) : commandes, livreurs,
 * motards, associations. Mêmes champs et mêmes filtres que l'API admin de l'application
 * (AdminService), pour que les pages MA affichent exactement les mêmes données.
 *
 * Les modifications, elles, passent toujours par l'API admin Colis na Nga (relais).
 */
@Service
public class CnnLectureService {

    static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");
    private static final int LIMITE = 2000;
    private static final Set<String> ORDER_STATUSES = Set.of("PENDING", "PENDING_MOTARD_ACCEPTANCE", "AGENT_EN_ROUTE",
            "LIVREUR_EN_ROUTE", "COURIER_AT_PICKUP", "AWAITING_PICKUP_PAYMENT", "COLLECTED", "IN_TRANSIT",
            "AWAITING_DELIVERY_PAYMENT", "DELIVERED", "CANCELLED");
    private static final Set<String> AGENT_STATUSES = Set.of("ON_DUTY", "OFF_DUTY");
    private static final Set<String> VALIDATION_STATUSES = Set.of("PENDING_VALIDATION", "ASSOCIATION_VALIDATED", "APPROVED", "REJECTED");

    private final CnnProperties properties;
    private final CnnReadOnlyClient db;
    private volatile Boolean binaryIds;

    public CnnLectureService(CnnProperties properties, @Qualifier("cnnJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc) {
        this.properties = properties;
        JdbcTemplate template = jdbc.getIfAvailable();
        this.db = template == null ? null : new CnnReadOnlyClient(template);
    }

    public boolean disponible() {
        return db != null;
    }

    CnnReadOnlyClient db() {
        return db;
    }

    // ── Commandes ──────────────────────────────────────────────────────────────

    public List<Map<String, Object>> commandes(String status, String search) {
        return commandes(status, search, null);
    }

    public List<Map<String, Object>> commandes(String status, String search, String clientId) {
        String st = allowed(status, ORDER_STATUSES);
        String q = like(search);
        // Hibernate nomme la clé du client « client_user_id » (Client hérite de User) : on lit le vrai nom.
        String clientCol = clientColumn();
        boolean byClient = clientId != null && clientCol != null;
        String clientIdCol = clientCol == null ? "NULL" : uid("o." + clientCol);
        String clientJoin = clientCol == null
                ? "LEFT JOIN clients c ON 1 = 0 LEFT JOIN users cu ON 1 = 0"
                : "LEFT JOIN clients c ON c.user_id = o." + clientCol + " LEFT JOIN users cu ON cu.id = o." + clientCol;
        String sql = "SELECT " + uid("o.id") + " AS id, " + clientIdCol + " AS client_id, "
                + uid("o.delivery_agent_id") + " AS delivery_agent_id, " + uid("o.partner_motard_id") + " AS partner_motard_id, "
                + """
                o.courier_type, o.pickup_address, o.delivery_address, o.pickup_contact_name, o.pickup_contact_phone,
                o.parcel_type, o.parcel_description, o.is_secret, o.requires_return_trip, o.estimated_distance_km,
                o.estimated_price, o.payment_timing, o.payment_mode, o.order_status, o.third_party_order, o.archived,
                o.created_at, o.updated_at, o.recipient_name, o.recipient_phone,
                c.first_name AS client_first_name, cu.phone AS client_phone
                FROM orders o
                """ + clientJoin + """

                WHERE (? IS NULL OR o.order_status = ?)
                  AND (? IS NULL OR LOWER(o.pickup_address) LIKE ? OR LOWER(o.delivery_address) LIKE ?)
                """ + (byClient ? "AND o." + clientCol + " = " + bin("?") + "\n" : "") + """
                ORDER BY o.created_at DESC
                LIMIT """ + " " + LIMITE;
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("clientId", rs.getString("client_id"));
            m.put("deliveryAgentId", rs.getString("delivery_agent_id"));
            m.put("partnerMotardId", rs.getString("partner_motard_id"));
            m.put("courierType", rs.getString("courier_type"));
            m.put("pickupAddress", rs.getString("pickup_address"));
            m.put("deliveryAddress", rs.getString("delivery_address"));
            m.put("pickupContactName", rs.getString("pickup_contact_name"));
            m.put("pickupContactPhone", rs.getString("pickup_contact_phone"));
            m.put("parcelType", rs.getString("parcel_type"));
            m.put("parcelDescription", rs.getString("parcel_description"));
            m.put("isSecret", bool(rs, "is_secret"));
            m.put("requiresReturnTrip", bool(rs, "requires_return_trip"));
            m.put("estimatedDistanceKm", rs.getBigDecimal("estimated_distance_km"));
            m.put("estimatedPrice", rs.getBigDecimal("estimated_price"));
            m.put("paymentTiming", rs.getString("payment_timing"));
            m.put("paymentMode", rs.getString("payment_mode"));
            m.put("orderStatus", rs.getString("order_status"));
            m.put("thirdPartyOrder", bool(rs, "third_party_order"));
            m.put("archived", bool(rs, "archived"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("updatedAt", iso(rs, "updated_at"));
            m.put("clientFirstName", rs.getString("client_first_name"));
            m.put("clientPhone", rs.getString("client_phone"));
            m.put("recipientName", rs.getString("recipient_name"));
            m.put("recipientPhone", rs.getString("recipient_phone"));
            return m;
        }, byClient ? new Object[] {st, st, q, q, q, clientId} : new Object[] {st, st, q, q, q});
    }

    // ── Livreurs ───────────────────────────────────────────────────────────────

    public List<Map<String, Object>> livreurs(String status, String search) {
        String st = allowed(status, AGENT_STATUSES);
        String q = like(search);
        String sql = "SELECT " + uid("u.id") + " AS id, " + """
                u.phone, u.email, u.profile_photo_url, d.first_name, d.last_name, d.status, d.latitude, d.longitude,
                d.last_position_at, d.active
                FROM delivery_agents d JOIN users u ON u.id = d.user_id
                WHERE u.phone NOT LIKE 'DELETED%'
                  AND (? IS NULL OR d.status = ?)
                  AND (? IS NULL OR LOWER(u.phone) LIKE ? OR LOWER(u.email) LIKE ?
                       OR LOWER(d.first_name) LIKE ? OR LOWER(d.last_name) LIKE ?)
                ORDER BY d.first_name, d.last_name
                LIMIT """ + " " + LIMITE;
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("phone", rs.getString("phone"));
            m.put("email", rs.getString("email"));
            m.put("status", rs.getString("status"));
            m.put("latitude", rs.getBigDecimal("latitude"));
            m.put("longitude", rs.getBigDecimal("longitude"));
            m.put("lastPositionAt", iso(rs, "last_position_at"));
            m.put("active", bool(rs, "active"));
            m.put("profilePhotoUrl", rs.getString("profile_photo_url"));
            m.put("firstName", rs.getString("first_name"));
            m.put("lastName", rs.getString("last_name"));
            return m;
        }, st, st, q, q, q, q, q);
    }

    // ── Motards ────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> motards(String validationStatus, String search) {
        String st = allowed(validationStatus, VALIDATION_STATUSES);
        String q = like(search);
        return motardsWhere("""
                (? IS NULL OR m.validation_status = ?)
                AND (? IS NULL OR LOWER(u.phone) LIKE ? OR LOWER(m.first_name) LIKE ? OR LOWER(m.last_name) LIKE ?
                     OR LOWER(m.moto_registration_number) LIKE ?)""", st, st, q, q, q, q, q);
    }

    public List<Map<String, Object>> motardsDeLAssociation(String associationId) {
        return motardsWhere("m.association_id = " + bin("?"), associationId);
    }

    private List<Map<String, Object>> motardsWhere(String condition, Object... args) {
        String sql = "SELECT " + uid("u.id") + " AS id, " + uid("m.association_id") + " AS association_id, "
                + uid("dr.id") + " AS deletion_id, " + """
                u.phone, u.email, u.profile_photo_url, m.first_name, m.last_name, m.moto_registration_number,
                m.validation_status, m.status, m.credit_balance, m.latitude, m.longitude, m.last_position_at, m.active,
                m.emergency_phone, a.name AS association_name, dr.admin_reason AS deletion_reason
                FROM partner_motards m
                JOIN users u ON u.id = m.user_id
                LEFT JOIN partner_associations a ON a.id = m.association_id
                LEFT JOIN motard_deletion_requests dr ON dr.motard_id = m.user_id AND dr.status = 'PENDING'
                WHERE u.phone NOT LIKE 'DELETED%' AND """ + " " + condition + """

                ORDER BY m.first_name, m.last_name
                LIMIT """ + " " + LIMITE;
        List<Map<String, Object>> rows = db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("phone", rs.getString("phone"));
            m.put("email", rs.getString("email"));
            m.put("firstName", rs.getString("first_name"));
            m.put("lastName", rs.getString("last_name"));
            m.put("motoRegistrationNumber", rs.getString("moto_registration_number"));
            m.put("associationId", rs.getString("association_id"));
            m.put("associationName", rs.getString("association_name"));
            m.put("validationStatus", rs.getString("validation_status"));
            m.put("status", rs.getString("status"));
            m.put("creditBalance", rs.getObject("credit_balance") == null ? null : rs.getInt("credit_balance"));
            m.put("latitude", rs.getBigDecimal("latitude"));
            m.put("longitude", rs.getBigDecimal("longitude"));
            m.put("lastPositionAt", iso(rs, "last_position_at"));
            m.put("active", bool(rs, "active"));
            m.put("profilePhotoUrl", rs.getString("profile_photo_url"));
            m.put("emergencyPhone", rs.getString("emergency_phone"));
            m.put("pendingDeletionRequestId", rs.getString("deletion_id"));
            m.put("pendingDeletionReason", rs.getString("deletion_reason"));
            return m;
        }, args);
        // Une seule ligne par motard même si plusieurs demandes de suppression sont ouvertes.
        Map<Object, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            unique.putIfAbsent(row.get("id"), row);
        }
        return new ArrayList<>(unique.values());
    }

    // ── Associations ───────────────────────────────────────────────────────────

    public List<Map<String, Object>> associations(String search) {
        String q = like(search);
        return associationsWhere("(? IS NULL OR LOWER(a.name) LIKE ? OR LOWER(u.phone) LIKE ?)", q, q, q);
    }

    public Map<String, Object> association(String id) {
        List<Map<String, Object>> rows = associationsWhere("a.id = " + bin("?"), id);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> m = rows.get(0);
        String base = "SELECT COUNT(*) FROM partner_motards pm JOIN users pu ON pu.id = pm.user_id "
                + "WHERE pu.phone NOT LIKE 'DELETED%' AND pm.association_id = " + bin("?");
        m.put("totalMotards", count(base, id));
        m.put("approvedMotards", count(base + " AND pm.validation_status = 'APPROVED'", id));
        m.put("pendingMotards", count(base + " AND pm.validation_status IN ('PENDING_VALIDATION', 'ASSOCIATION_VALIDATED')", id));
        return m;
    }

    private List<Map<String, Object>> associationsWhere(String condition, Object... args) {
        String sql = "SELECT " + uid("a.id") + " AS id, " + """
                a.name, u.phone, u.email, a.active, a.total_revenue, u.created_at, u.profile_photo_url, a.commission_percent
                FROM partner_associations a JOIN users u ON u.id = a.id
                WHERE u.phone NOT LIKE 'DELETED%' AND """ + " " + condition + """

                ORDER BY a.name
                LIMIT """ + " " + LIMITE;
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("name", rs.getString("name"));
            m.put("phone", rs.getString("phone"));
            m.put("email", rs.getString("email"));
            m.put("active", bool(rs, "active"));
            m.put("totalRevenue", rs.getBigDecimal("total_revenue"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("profilePhotoUrl", rs.getString("profile_photo_url"));
            m.put("commissionPercent", rs.getBigDecimal("commission_percent"));
            return m;
        }, args);
    }

    // ── Carte en direct ────────────────────────────────────────────────────────

    /** Statuts d'une course encore en cours (ni livrée ni annulée). */
    private static final String ACTIVE_ORDERS = "('PENDING', 'PENDING_MOTARD_ACCEPTANCE', 'AGENT_EN_ROUTE', 'LIVREUR_EN_ROUTE', "
            + "'COURIER_AT_PICKUP', 'AWAITING_PICKUP_PAYMENT', 'COLLECTED', 'IN_TRANSIT', 'AWAITING_DELIVERY_PAYMENT')";

    /**
     * Tout ce qui bouge sur Colis na Nga, pour la carte de suivi : livreurs (motards de l'entreprise),
     * motards partenaires, demandes des clients, courses en cours, associations, urgences et
     * activité récente. Chaque bloc est lu séparément : un bloc illisible vaut null sans bloquer les autres.
     */
    public Map<String, Object> carte() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("calculeLe", java.time.OffsetDateTime.now(KINSHASA).toString());
        body.put("livreurs", section(this::carteLivreurs));
        body.put("motards", section(this::carteMotards));
        body.put("courses", section(this::carteCourses));
        body.put("associations", section(this::carteAssociations));
        body.put("urgences", section(this::carteUrgences));
        body.put("activite", section(this::carteActivite));
        return body;
    }

    <T> T section(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CnnLectureService.class).warn("Carte Colis na Nga : bloc illisible : {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> carteLivreurs() {
        String sql = "SELECT " + uid("u.id") + " AS id, " + """
                u.phone, u.profile_photo_url, d.first_name, d.last_name, d.status, d.active,
                d.latitude, d.longitude, d.last_position_at,
                (SELECT COUNT(*) FROM orders o WHERE o.delivery_agent_id = d.user_id AND o.archived = FALSE
                   AND o.order_status IN """ + " " + ACTIVE_ORDERS + """
                ) AS courses
                FROM delivery_agents d JOIN users u ON u.id = d.user_id
                WHERE u.phone NOT LIKE 'DELETED%'
                ORDER BY d.status = 'ON_DUTY' DESC, d.last_position_at DESC""";
        return db.query(sql, (rs, i) -> courier(rs, "LIVREUR"));
    }

    private List<Map<String, Object>> carteMotards() {
        String sql = "SELECT " + uid("u.id") + " AS id, " + uid("m.association_id") + " AS association_id, " + """
                u.phone, u.profile_photo_url, m.first_name, m.last_name, m.status, m.active,
                m.latitude, m.longitude, m.last_position_at, m.credit_balance, a.name AS association_name,
                (SELECT COUNT(*) FROM orders o WHERE o.partner_motard_id = m.user_id AND o.archived = FALSE
                   AND o.order_status IN """ + " " + ACTIVE_ORDERS + """
                ) AS courses
                FROM partner_motards m
                JOIN users u ON u.id = m.user_id
                LEFT JOIN partner_associations a ON a.id = m.association_id
                WHERE u.phone NOT LIKE 'DELETED%' AND m.validation_status = 'APPROVED'
                ORDER BY m.status = 'ON_DUTY' DESC, m.last_position_at DESC""";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = courier(rs, "MOTARD");
            m.put("associationId", rs.getString("association_id"));
            m.put("associationName", rs.getString("association_name"));
            m.put("creditBalance", rs.getObject("credit_balance") == null ? null : rs.getInt("credit_balance"));
            return m;
        });
    }

    private Map<String, Object> courier(ResultSet rs, String type) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("type", type);
        m.put("firstName", rs.getString("first_name"));
        m.put("lastName", rs.getString("last_name"));
        m.put("phone", rs.getString("phone"));
        m.put("photo", rs.getString("profile_photo_url"));
        m.put("status", rs.getString("status"));
        m.put("active", bool(rs, "active"));
        m.put("lat", rs.getBigDecimal("latitude"));
        m.put("lng", rs.getBigDecimal("longitude"));
        m.put("lastPositionAt", iso(rs, "last_position_at"));
        m.put("courses", rs.getLong("courses"));
        return m;
    }

    private List<Map<String, Object>> carteCourses() {
        String clientCol = clientColumn();
        String client = clientCol == null ? "NULL AS client_first_name, NULL AS client_phone"
                : "(SELECT c.first_name FROM clients c WHERE c.user_id = o." + clientCol + ") AS client_first_name, "
                + "(SELECT cu.phone FROM users cu WHERE cu.id = o." + clientCol + ") AS client_phone";
        String sql = "SELECT " + uid("o.id") + " AS id, " + uid("o.delivery_agent_id") + " AS agent_id, "
                + uid("o.partner_motard_id") + " AS motard_id, " + client + ", " + """
                o.order_status, o.courier_type, o.pickup_address, o.delivery_address,
                o.pickup_latitude, o.pickup_longitude, o.delivery_latitude, o.delivery_longitude,
                o.estimated_price, o.created_at, o.updated_at
                FROM orders o
                WHERE o.archived = FALSE AND o.order_status IN """ + " " + ACTIVE_ORDERS + """

                ORDER BY o.created_at DESC
                LIMIT 500""";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("status", rs.getString("order_status"));
            m.put("courierType", rs.getString("courier_type"));
            m.put("agentId", rs.getString("agent_id"));
            m.put("motardId", rs.getString("motard_id"));
            m.put("clientFirstName", rs.getString("client_first_name"));
            m.put("clientPhone", rs.getString("client_phone"));
            m.put("pickupAddress", rs.getString("pickup_address"));
            m.put("deliveryAddress", rs.getString("delivery_address"));
            m.put("pickupLat", rs.getBigDecimal("pickup_latitude"));
            m.put("pickupLng", rs.getBigDecimal("pickup_longitude"));
            m.put("deliveryLat", rs.getBigDecimal("delivery_latitude"));
            m.put("deliveryLng", rs.getBigDecimal("delivery_longitude"));
            m.put("price", rs.getBigDecimal("estimated_price"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("updatedAt", iso(rs, "updated_at"));
            return m;
        });
    }

    private List<Map<String, Object>> carteAssociations() {
        String sql = "SELECT " + uid("a.id") + " AS id, " + """
                a.name, a.active,
                (SELECT COUNT(*) FROM partner_motards m JOIN users mu ON mu.id = m.user_id
                   WHERE m.association_id = a.id AND mu.phone NOT LIKE 'DELETED%' AND m.validation_status = 'APPROVED') AS motards,
                (SELECT COUNT(*) FROM partner_motards m
                   WHERE m.association_id = a.id AND m.validation_status = 'APPROVED' AND m.status = 'ON_DUTY' AND m.active = TRUE) AS en_service
                FROM partner_associations a JOIN users u ON u.id = a.id
                WHERE u.phone NOT LIKE 'DELETED%'
                ORDER BY a.name""";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("name", rs.getString("name"));
            m.put("active", bool(rs, "active"));
            m.put("motards", rs.getLong("motards"));
            m.put("enService", rs.getLong("en_service"));
            return m;
        });
    }

    private List<Map<String, Object>> carteUrgences() {
        LocalDateTime since = LocalDateTime.now(storageZone()).minusHours(24);
        String sql = "SELECT " + uid("e.id") + " AS id, " + uid("e.order_id") + " AS order_id, " + """
                e.caller_name, e.caller_role, e.reason, e.called_at
                FROM emergency_calls e WHERE e.called_at >= ? ORDER BY e.called_at DESC LIMIT 20""";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("orderId", rs.getString("order_id"));
            m.put("callerName", rs.getString("caller_name"));
            m.put("callerRole", rs.getString("caller_role"));
            m.put("reason", rs.getString("reason"));
            m.put("calledAt", iso(rs, "called_at"));
            return m;
        }, since);
    }

    /** Dernières commandes créées ou mises à jour (journal d'activité). */
    private List<Map<String, Object>> carteActivite() {
        String clientCol = clientColumn();
        String client = clientCol == null ? "NULL AS client_first_name"
                : "(SELECT c.first_name FROM clients c WHERE c.user_id = o." + clientCol + ") AS client_first_name";
        String sql = "SELECT " + uid("o.id") + " AS id, " + client + ", " + """
                o.order_status, o.delivery_address, o.created_at, COALESCE(o.updated_at, o.created_at) AS at
                FROM orders o
                ORDER BY COALESCE(o.updated_at, o.created_at) DESC
                LIMIT 20""";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("status", rs.getString("order_status"));
            m.put("clientFirstName", rs.getString("client_first_name"));
            m.put("deliveryAddress", rs.getString("delivery_address"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("at", iso(rs, "at"));
            return m;
        });
    }



    // ── Clients ────────────────────────────────────────────────────────────────

    private static final Set<String> CLIENT_TYPES = Set.of("ONE_TIME", "SUBSCRIBED");

    /** Tous les clients (ponctuels et abonnés) avec leur activité : commandes, livrées, dépense, dernière commande. */
    public List<Map<String, Object>> clients(String type, String search) {
        String t = allowed(type, CLIENT_TYPES);
        String q = like(search);
        String cc = clientColumn();
        String activity = cc == null
                ? "LEFT JOIN (SELECT NULL AS cid, 0 AS n, 0 AS l, 0 AS d, NULL AS last) o ON 1 = 0"
                : "LEFT JOIN (SELECT " + cc + " AS cid, COUNT(*) AS n, SUM(order_status = 'DELIVERED') AS l, "
                + "COALESCE(SUM(CASE WHEN order_status = 'DELIVERED' THEN estimated_price END), 0) AS d, MAX(created_at) AS last "
                + "FROM orders GROUP BY " + cc + ") o ON o.cid = c.user_id";
        String kind = "(CASE WHEN sc.client_id IS NOT NULL THEN 'SUBSCRIBED' ELSE c.type END)";
        String sql = "SELECT " + uid("u.id") + " AS id, " + kind + " AS kind, "
                + "u.phone, u.email, u.profile_photo_url, u.created_at, c.first_name, c.last_name, c.active, "
                + "sc.company_name, sc.status AS sub_status, sc.validated_at, sp.plan_type, "
                + "COALESCE(o.n, 0) AS commandes, COALESCE(o.l, 0) AS livrees, COALESCE(o.d, 0) AS depense, o.last AS derniere "
                + "FROM clients c JOIN users u ON u.id = c.user_id "
                + "LEFT JOIN subscribed_clients sc ON sc.client_id = c.user_id "
                + "LEFT JOIN subscriptions sp ON sp.id = sc.subscription_id "
                + activity + " "
                + "WHERE u.phone NOT LIKE 'DELETED%' "
                + "AND (? IS NULL OR " + kind + " = ?) "
                + "AND (? IS NULL OR LOWER(u.phone) LIKE ? OR LOWER(u.email) LIKE ? OR LOWER(c.first_name) LIKE ? "
                + "OR LOWER(c.last_name) LIKE ? OR LOWER(sc.company_name) LIKE ?) "
                + "ORDER BY u.created_at DESC LIMIT " + LIMITE;
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("type", rs.getString("kind"));
            m.put("firstName", rs.getString("first_name"));
            m.put("lastName", rs.getString("last_name"));
            m.put("phone", rs.getString("phone"));
            m.put("email", rs.getString("email"));
            m.put("profilePhotoUrl", rs.getString("profile_photo_url"));
            m.put("active", bool(rs, "active"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("companyName", rs.getString("company_name"));
            m.put("subscriptionStatus", rs.getString("sub_status"));
            m.put("validatedAt", iso(rs, "validated_at"));
            m.put("planType", rs.getString("plan_type"));
            m.put("orders", rs.getLong("commandes"));
            m.put("delivered", rs.getLong("livrees"));
            m.put("spent", rs.getBigDecimal("depense"));
            m.put("lastOrderAt", iso(rs, "derniere"));
            return m;
        }, t, t, q, q, q, q, q, q);
    }

    // ── Crédits des motards ────────────────────────────────────────────────────

    /** Achats et attributions de crédits des motards, les plus récents d'abord. */
    public List<Map<String, Object>> credits() {
        String mc = "s." + fk("motard_credit_subscriptions", "motard_user_id", "motard_id");
        String sql = "SELECT " + uid("s.id") + " AS id, " + uid(mc) + " AS motard_id, " + """
                s.credits_purchased, s.amount_paid, s.association_revenue, s.payment_mode, s.is_free_starter, s.purchased_at,
                m.first_name, m.last_name, u.phone, a.name AS association_name
                FROM motard_credit_subscriptions s
                """ + "LEFT JOIN partner_motards m ON m.user_id = " + mc + "\n"
                + "LEFT JOIN users u ON u.id = " + mc + "\n" + """
                LEFT JOIN partner_associations a ON a.id = m.association_id
                ORDER BY s.purchased_at DESC
                LIMIT """ + " " + LIMITE;
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("motardId", rs.getString("motard_id"));
            String name = String.join(" ", java.util.stream.Stream.of(rs.getString("first_name"), rs.getString("last_name"))
                    .filter(x -> x != null && !x.isBlank()).toList());
            m.put("motardName", name.isBlank() ? null : name);
            m.put("motardPhone", rs.getString("phone"));
            m.put("associationName", rs.getString("association_name"));
            m.put("creditsPurchased", rs.getInt("credits_purchased"));
            m.put("amountPaid", rs.getBigDecimal("amount_paid"));
            m.put("associationRevenue", rs.getBigDecimal("association_revenue"));
            m.put("paymentMode", rs.getString("payment_mode"));
            m.put("isFreeStarter", bool(rs, "is_free_starter"));
            m.put("purchasedAt", iso(rs, "purchased_at"));
            return m;
        });
    }

    // ── Itinéraire d'une commande ──────────────────────────────────────────────

    /**
     * Recherche de commandes pour la carte (en cours ou passées) : identifiant, client, destinataire,
     * coursier ou adresse. Les 60 plus récentes correspondantes.
     */
    public List<Map<String, Object>> rechercheCourses(String search, String status) {
        String st = allowed(status, ORDER_STATUSES);
        String q = like(search);
        String clientCol = clientColumn();
        String clientJoin = clientCol == null
                ? "LEFT JOIN clients c ON 1 = 0 LEFT JOIN users cu ON 1 = 0"
                : "LEFT JOIN clients c ON c.user_id = o." + clientCol + " LEFT JOIN users cu ON cu.id = o." + clientCol;
        String sql = "SELECT " + uid("o.id") + " AS id, " + """
                o.order_status, o.courier_type, o.pickup_address, o.delivery_address, o.estimated_price,
                o.created_at, o.updated_at, o.recipient_name,
                c.first_name AS client_first_name, c.last_name AS client_last_name, cu.phone AS client_phone,
                COALESCE(d.first_name, m.first_name) AS courier_first_name, COALESCE(d.last_name, m.last_name) AS courier_last_name
                FROM orders o
                """ + clientJoin + """

                LEFT JOIN delivery_agents d ON d.user_id = o.delivery_agent_id
                LEFT JOIN partner_motards m ON m.user_id = o.partner_motard_id
                WHERE (? IS NULL OR o.order_status = ?)
                  AND (? IS NULL OR LOWER(""" + uid("o.id") + """
                ) LIKE ? OR LOWER(o.pickup_address) LIKE ? OR LOWER(o.delivery_address) LIKE ?
                       OR LOWER(c.first_name) LIKE ? OR LOWER(c.last_name) LIKE ? OR LOWER(cu.phone) LIKE ?
                       OR LOWER(o.recipient_name) LIKE ? OR LOWER(o.recipient_phone) LIKE ?
                       OR LOWER(d.first_name) LIKE ? OR LOWER(d.last_name) LIKE ?
                       OR LOWER(m.first_name) LIKE ? OR LOWER(m.last_name) LIKE ?)
                ORDER BY o.created_at DESC
                LIMIT 60""";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("status", rs.getString("order_status"));
            m.put("courierType", rs.getString("courier_type"));
            m.put("pickupAddress", rs.getString("pickup_address"));
            m.put("deliveryAddress", rs.getString("delivery_address"));
            m.put("price", rs.getBigDecimal("estimated_price"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("updatedAt", iso(rs, "updated_at"));
            m.put("recipientName", rs.getString("recipient_name"));
            m.put("clientFirstName", rs.getString("client_first_name"));
            m.put("clientLastName", rs.getString("client_last_name"));
            m.put("clientPhone", rs.getString("client_phone"));
            m.put("courierFirstName", rs.getString("courier_first_name"));
            m.put("courierLastName", rs.getString("courier_last_name"));
            return m;
        }, st, st, q, q, q, q, q, q, q, q, q, q, q, q, q);
    }

    /**
     * Itinéraire complet d'une commande : départ, arrêts, chronologie des statuts et tracé GPS
     * enregistré par Colis na Nga pendant la course (table order_track_points).
     */
    public Map<String, Object> itineraire(String id) {
        String clientCol = clientColumn();
        String client = clientCol == null ? "NULL AS client_first_name, NULL AS client_last_name, NULL AS client_phone"
                : "(SELECT c.first_name FROM clients c WHERE c.user_id = o." + clientCol + ") AS client_first_name, "
                + "(SELECT c.last_name FROM clients c WHERE c.user_id = o." + clientCol + ") AS client_last_name, "
                + "(SELECT cu.phone FROM users cu WHERE cu.id = o." + clientCol + ") AS client_phone";
        String sql = "SELECT " + uid("o.id") + " AS id, " + uid("o.delivery_agent_id") + " AS agent_id, "
                + uid("o.partner_motard_id") + " AS motard_id, " + client + ", " + """
                o.order_status, o.courier_type, o.pickup_address, o.delivery_address,
                o.pickup_latitude, o.pickup_longitude, o.delivery_latitude, o.delivery_longitude,
                o.pickup_contact_name, o.pickup_contact_phone, o.recipient_name, o.recipient_phone,
                o.parcel_type, o.estimated_distance_km, o.estimated_price, o.payment_mode, o.payment_timing,
                o.requires_return_trip, o.created_at, o.updated_at
                FROM orders o WHERE o.id = """ + " " + bin("?");
        List<Map<String, Object>> found = db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("status", rs.getString("order_status"));
            m.put("courierType", rs.getString("courier_type"));
            m.put("agentId", rs.getString("agent_id"));
            m.put("motardId", rs.getString("motard_id"));
            m.put("clientFirstName", rs.getString("client_first_name"));
            m.put("clientLastName", rs.getString("client_last_name"));
            m.put("clientPhone", rs.getString("client_phone"));
            m.put("pickupAddress", rs.getString("pickup_address"));
            m.put("deliveryAddress", rs.getString("delivery_address"));
            m.put("pickupLat", rs.getBigDecimal("pickup_latitude"));
            m.put("pickupLng", rs.getBigDecimal("pickup_longitude"));
            m.put("deliveryLat", rs.getBigDecimal("delivery_latitude"));
            m.put("deliveryLng", rs.getBigDecimal("delivery_longitude"));
            m.put("pickupContactName", rs.getString("pickup_contact_name"));
            m.put("pickupContactPhone", rs.getString("pickup_contact_phone"));
            m.put("recipientName", rs.getString("recipient_name"));
            m.put("recipientPhone", rs.getString("recipient_phone"));
            m.put("parcelType", rs.getString("parcel_type"));
            m.put("distanceKm", rs.getBigDecimal("estimated_distance_km"));
            m.put("price", rs.getBigDecimal("estimated_price"));
            m.put("paymentMode", rs.getString("payment_mode"));
            m.put("paymentTiming", rs.getString("payment_timing"));
            m.put("returnTrip", bool(rs, "requires_return_trip"));
            m.put("createdAt", iso(rs, "created_at"));
            m.put("updatedAt", iso(rs, "updated_at"));
            return m;
        }, id);
        if (found.isEmpty()) {
            return null;
        }
        Map<String, Object> order = found.get(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("commande", order);
        body.put("coursier", section(() -> coursierDeLaCommande(order)));
        body.put("arrets", section(() -> arrets(id)));
        body.put("chronologie", section(() -> chronologie(id)));
        Object trace = section(() -> trace(id));
        body.put("trace", trace);
        body.put("traceRoute", traceRoute(trace));
        return body;
    }

    /**
     * Trace GPS collée aux routes par la Roads API de Google, via le backend Colis na Nga
     * (POST /api/public/geo/snap). Null si la trace est trop courte ou si le service ne répond
     * pas : l'écran garde alors son propre alignement (Directions) ou la trace brute.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> traceRoute(Object trace) {
        if (!(trace instanceof List<?> list) || list.size() < 2) return null;
        try {
            List<Map<String, Object>> points = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                points.add(Map.of("lat", m.get("lat"), "lng", m.get("lng")));
            }
            String body = SNAP_JSON.writeValueAsString(Map.of("points", points));
            String base = properties.getApi().getBaseUrl().replaceAll("/+$", "");
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/public/geo/snap"))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            java.net.http.HttpResponse<String> res = SNAP_HTTP.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            com.fasterxml.jackson.databind.JsonNode coords = SNAP_JSON.readTree(res.body()).path("coordinates");
            List<Map<String, Object>> out = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode c : coords) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("lat", c.path("latitude").asDouble());
                m.put("lng", c.path("longitude").asDouble());
                out.add(m);
            }
            return out.size() >= 2 ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper SNAP_JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final java.net.http.HttpClient SNAP_HTTP = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5)).build();

    private Map<String, Object> coursierDeLaCommande(Map<String, Object> order) {
        String agentId = (String) order.get("agentId");
        String motardId = (String) order.get("motardId");
        if (agentId == null && motardId == null) return null;
        boolean agent = agentId != null;
        String sql = "SELECT " + uid("u.id") + " AS id, u.phone, u.profile_photo_url, x.first_name, x.last_name, x.status, "
                + "x.latitude, x.longitude, x.last_position_at FROM "
                + (agent ? "delivery_agents" : "partner_motards") + " x JOIN users u ON u.id = x.user_id WHERE x.user_id = " + bin("?");
        List<Map<String, Object>> rows = db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("type", agent ? "LIVREUR" : "MOTARD");
            m.put("firstName", rs.getString("first_name"));
            m.put("lastName", rs.getString("last_name"));
            m.put("phone", rs.getString("phone"));
            m.put("photo", rs.getString("profile_photo_url"));
            m.put("status", rs.getString("status"));
            m.put("lat", rs.getBigDecimal("latitude"));
            m.put("lng", rs.getBigDecimal("longitude"));
            m.put("lastPositionAt", iso(rs, "last_position_at"));
            return m;
        }, agent ? agentId : motardId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> arrets(String orderId) {
        String sql = """
                SELECT p.stop_order, p.tracking_number, p.delivery_address, p.delivery_latitude, p.delivery_longitude,
                       p.recipient_name, p.recipient_phone, p.delivery_status, p.payment_status
                FROM parcels p WHERE p.order_id = """ + " " + bin("?") + " ORDER BY p.stop_order";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stopOrder", rs.getInt("stop_order"));
            m.put("trackingNumber", rs.getString("tracking_number"));
            m.put("address", rs.getString("delivery_address"));
            m.put("lat", rs.getBigDecimal("delivery_latitude"));
            m.put("lng", rs.getBigDecimal("delivery_longitude"));
            m.put("recipientName", rs.getString("recipient_name"));
            m.put("recipientPhone", rs.getString("recipient_phone"));
            m.put("deliveryStatus", rs.getString("delivery_status"));
            m.put("paymentStatus", rs.getString("payment_status"));
            return m;
        }, orderId);
    }

    /** Statuts de la commande, horodatés (un statut répété sur plusieurs colis n'apparaît qu'une fois). */
    private List<Map<String, Object>> chronologie(String orderId) {
        String sql = """
                SELECT h.status, h.`timestamp` AS at, h.triggered_by_role, p.stop_order
                FROM status_histories h JOIN parcels p ON p.id = h.parcel_id
                WHERE p.order_id = """ + " " + bin("?") + " ORDER BY h.`timestamp`, p.stop_order";
        List<Map<String, Object>> rows = db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", rs.getString("status"));
            m.put("at", iso(rs, "at"));
            m.put("role", rs.getString("triggered_by_role"));
            m.put("stopOrder", rs.getInt("stop_order"));
            return m;
        }, orderId);
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String at = String.valueOf(row.get("at"));
            String key = row.get("status") + "|" + (at.length() >= 16 ? at.substring(0, 16) : at);
            unique.putIfAbsent(key, row);
        }
        return new ArrayList<>(unique.values());
    }

    /** Tracé GPS enregistré pendant la course ; liste vide si rien n'a été enregistré. */
    private List<Map<String, Object>> trace(String orderId) {
        String sql = "SELECT t.latitude, t.longitude, t.recorded_at, t.courier_type FROM order_track_points t "
                + "WHERE t.order_id = " + bin("?") + " ORDER BY t.recorded_at LIMIT 5000";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lat", rs.getDouble("latitude"));
            m.put("lng", rs.getDouble("longitude"));
            m.put("at", iso(rs, "recorded_at"));
            return m;
        }, orderId);
    }

    ZoneId storageZone() {
        try {
            return ZoneId.of(properties.getStorageZone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    // ── Outils ─────────────────────────────────────────────────────────────────

    /** Hibernate 6 stocke les UUID en BINARY(16) sur MySQL ; on s'adapte au type réel de la colonne. */
    boolean binaryIds() {
        Boolean known = binaryIds;
        if (known == null) {
            String type = db.queryForObject("""
                    SELECT DATA_TYPE FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'id'""", String.class);
            known = type != null && type.toLowerCase(Locale.ROOT).contains("binary");
            binaryIds = known;
        }
        return known;
    }

    private volatile String clientColumn;
    private final Map<String, String> foreignKeys = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Nom réel d'une clé étrangère : Hibernate la nomme d'après la clé primaire de la cible
     * (ex. motard → motard_user_id, car PartnerMotard hérite de User). On lit la base plutôt que deviner.
     */
    String fk(String table, String... candidates) {
        String key = table + ":" + String.join(",", candidates);
        String known = foreignKeys.get(key);
        if (known != null) return known;
        List<String> cols = db.query("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?""", (rs, i) -> rs.getString(1).toLowerCase(Locale.ROOT), table);
        String found = null;
        for (String c : candidates) {
            if (cols.contains(c)) {
                found = c;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Colonne introuvable dans " + table + " : " + String.join(" / ", candidates));
        }
        foreignKeys.put(key, found);
        return found;
    }

    String clientColumn() {
        String known = clientColumn;
        if (known == null) {
            List<String> found = db.query("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'orders'
                      AND column_name IN ('client_user_id', 'client_id')
                    ORDER BY column_name = 'client_user_id' DESC""", (rs, i) -> rs.getString(1));
            known = found.isEmpty() ? "" : found.get(0);
            clientColumn = known;
        }
        return known.isEmpty() ? null : known;
    }

    String uid(String column) {
        return binaryIds() ? "BIN_TO_UUID(" + column + ")" : column;
    }

    String bin(String placeholder) {
        return binaryIds() ? "UUID_TO_BIN(" + placeholder + ")" : placeholder;
    }

    private Long count(String sql, Object... args) {
        Number n = db.queryForObject(sql, Number.class, args);
        return n == null ? 0L : n.longValue();
    }

    private static String allowed(String value, Set<String> values) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        return values.contains(v) ? v : null;
    }

    static String like(String search) {
        if (search == null || search.isBlank()) return null;
        String q = search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + q + "%";
    }

    static Boolean bool(ResultSet rs, String column) throws SQLException {
        Object v = rs.getObject(column);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof byte[] bytes) return bytes.length > 0 && bytes[0] != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    String iso(ResultSet rs, String column) throws SQLException {
        LocalDateTime at = rs.getObject(column, LocalDateTime.class);
        if (at == null) return null;
        ZoneId storage;
        try {
            storage = ZoneId.of(properties.getStorageZone());
        } catch (Exception e) {
            storage = ZoneId.of("UTC");
        }
        return at.atZone(storage).withZoneSameInstant(KINSHASA).toOffsetDateTime().toString();
    }

    static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
