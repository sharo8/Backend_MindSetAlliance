package com.mindsetalliance.core.cnn;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CnnDatabaseReaderTest {

    private static final String PHONE = "0999000111";
    private static final String CLIENT = "Client Secret";

    private JdbcTemplate jdbc;
    private CnnDatabaseReader reader;
    private LocalDate today;

    @BeforeEach
    void schema() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cnn-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE orders (
                    id VARCHAR(40) PRIMARY KEY,
                    order_status VARCHAR(50) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE status_histories (
                    id VARCHAR(40) PRIMARY KEY,
                    status VARCHAR(50) NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    order_id VARCHAR(40)
                )
                """);
        jdbc.execute("""
                CREATE TABLE partner_motards (
                    user_id VARCHAR(40) PRIMARY KEY,
                    first_name VARCHAR(80),
                    active BOOLEAN NOT NULL,
                    validation_status VARCHAR(40) NOT NULL,
                    courses_suspended BOOLEAN
                )
                """);
        jdbc.execute("""
                CREATE TABLE emergency_calls (
                    id VARCHAR(40) PRIMARY KEY,
                    called_at TIMESTAMP NOT NULL,
                    caller_name VARCHAR(80),
                    phone_dialed VARCHAR(40),
                    reason VARCHAR(200)
                )
                """);
        today = LocalDate.now(CnnDatabaseReader.ZONE);
        insertOrder("o1", "DELIVERED", today.atTime(8, 0));
        insertHistory("h1", "DELIVERED", today.atTime(8, 5), "o1");
        insertOrder("o2", "DELIVERED", today.minusDays(10).atTime(11, 0));
        insertHistory("h2", "DELIVERED", today.minusDays(10).atTime(11, 15), "o2");
        insertOrder("o3", "CANCELLED", today.minusDays(3).atTime(9, 0));
        insertOrder("o4", "PENDING", today.atTime(12, 0));
        jdbc.update("INSERT INTO partner_motards (user_id, first_name, active, validation_status, courses_suspended) VALUES (?,?,?,?,?)",
                "m1", "Motard Secret", true, "APPROVED", false);
        jdbc.update("INSERT INTO partner_motards (user_id, first_name, active, validation_status, courses_suspended) VALUES (?,?,?,?,?)",
                "m2", "Autre Motard", true, "APPROVED", false);
        jdbc.update("INSERT INTO partner_motards (user_id, first_name, active, validation_status, courses_suspended) VALUES (?,?,?,?,?)",
                "m3", "En attente", true, "PENDING_VALIDATION", false);
        jdbc.update("INSERT INTO emergency_calls (id, called_at, caller_name, phone_dialed, reason) VALUES (?,?,?,?,?)",
                "e1", java.sql.Timestamp.valueOf(today.atTime(7, 0)), CLIENT, PHONE, "adresse précise secrète");

        CnnProperties properties = new CnnProperties();
        properties.setMode("db");
        properties.setCacheSeconds(30);
        reader = new CnnDatabaseReader(properties, jdbc);
    }

    @Test
    void agregeQuatreTablesSansDonneePersonnelle() {
        CnnSnapshot snap = reader.snapshot();

        assertTrue(snap.available());
        assertEquals("CNN_DB", snap.source());
        assertEquals(1L, snap.coursesLivreesJour());
        assertEquals(2L, snap.coursesLivreesPeriode());
        assertEquals(2L, snap.coursesLivreesTotal());
        assertEquals(1L, snap.coursesAnnulees());
        assertEquals(2L, snap.coursiersActifs());
        assertNull(snap.coursiersExpires());
        assertNull(snap.abonnementsRegles());
        assertEquals(1L, snap.urgences());
        assertNull(snap.incidentsOuverts());
        assertFalse(snap.parStatut().isEmpty());
        assertEquals(30, snap.volume30j().size());
        assertEquals(7, snap.activite7j().size());
        assertTrue(snap.delaiDisponible());
        assertEquals(2L, ((Number) snap.delaiBins().get(0).get("occurrences")).longValue());
        assertTrue(snap.parStatut().stream().anyMatch(row -> "Livrée".equals(row.get("libelle"))));
        assertTrue(snap.parStatut().stream().anyMatch(row -> "PENDING".equals(row.get("libelle"))));
        Map<String, Object> accueil = reader.accueil("CNN");
        assertEquals("CNN", accueil.get("vue"));
        assertEquals(7, ((List<?>) accueil.get("activiteParJour")).size());

        String dumped = String.valueOf(snap.incidents()) + snap.parStatut() + reader.list("COURIER") + reader.list("INCIDENT");
        assertFalse(dumped.contains(PHONE));
        assertFalse(dumped.contains(CLIENT));
        assertFalse(dumped.contains("Motard Secret"));
        assertFalse(dumped.contains("adresse précise"));
    }

    @Test
    void leCacheEviteUneDeuxiemeLecture() {
        assertEquals(1L, reader.snapshot().coursesLivreesJour());
        insertHistory("h3", "DELIVERED", today.atTime(15, 0), "o4");
        assertEquals(1L, reader.snapshot().coursesLivreesJour());
    }

    @Test
    void urlAbsenteNeRenvoiePasDeZero() {
        CnnProperties properties = new CnnProperties();
        properties.setMode("db");
        CnnSnapshot snap = new CnnDatabaseReader(properties, (JdbcTemplate) null).snapshot();
        assertFalse(snap.available());
        assertEquals("INDISPONIBLE", snap.source());
        assertNull(snap.coursesLivreesJour());
        assertNull(snap.coursiersActifs());
        assertNull(snap.incidentsOuverts());
        Map<String, Object> dash = new CnnDatabaseReader(properties, (JdbcTemplate) null).dashboard("CNN");
        assertEquals(Boolean.FALSE, dash.get("disponible"));
        assertEquals("INDISPONIBLE", dash.get("source"));
        assertNull(dash.get("coursesLivreesJour"));
        assertTrue(dash.get("message").toString().toLowerCase(java.util.Locale.ROOT).contains("indisponible"));
    }

    @Test
    void lesListesRestentDesComptages() {
        List<Map<String, Object>> courses = reader.list("COURSE");
        assertTrue(courses.stream().allMatch(row -> Boolean.TRUE.equals(row.get("lectureSeule"))));
        assertTrue(courses.stream().anyMatch(row -> "DELIVERED".equals(row.get("statut"))));
        assertTrue(courses.stream().noneMatch(row -> String.valueOf(row.get("titre")).contains(CLIENT)));
    }

    private void insertOrder(String id, String status, LocalDateTime at) {
        jdbc.update("INSERT INTO orders (id, order_status, created_at, updated_at) VALUES (?,?,?,?)",
                id, status, java.sql.Timestamp.valueOf(at), java.sql.Timestamp.valueOf(at.plusMinutes(20)));
    }

    private void insertHistory(String id, String status, LocalDateTime at, String orderId) {
        jdbc.update("INSERT INTO status_histories (id, status, timestamp, order_id) VALUES (?,?,?,?)",
                id, status, java.sql.Timestamp.valueOf(at), orderId);
    }
}
