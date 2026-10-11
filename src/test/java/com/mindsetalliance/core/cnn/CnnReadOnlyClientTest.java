package com.mindsetalliance.core.cnn;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CnnReadOnlyClientTest {

    private static final Pattern SQL = Pattern.compile("(?s)\"\"\".*?\"\"\"|\"SELECT[^\"]*\"");
    private static final List<String> PERSONAL = List.of(
            "first_name", "last_name", "phone", "email", "address", "password", "token",
            "photo", "signature", "caller_name", "phone_dialed", "recipient_name",
            "moto_registration", "security_code", "payment_phone", "prenom", "adresse", "plaque"
    );

    @Test
    void uneEcritureEstRejeteeAvantLaBase() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cnn-ro-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE orders (id INT PRIMARY KEY, order_status VARCHAR(20))");
        jdbc.update("INSERT INTO orders (id, order_status) VALUES (1, 'DELIVERED')");
        CnnReadOnlyClient client = new CnnReadOnlyClient(jdbc);

        assertThrows(CnnWriteRejectedException.class, () -> client.execute("INSERT INTO orders (id, order_status) VALUES (2, 'X')"));
        assertThrows(CnnWriteRejectedException.class, () -> client.execute("UPDATE orders SET order_status = 'X'"));
        assertThrows(CnnWriteRejectedException.class, () -> client.execute("DELETE FROM orders"));
        assertThrows(CnnWriteRejectedException.class, () -> client.query("SELECT id FROM orders FOR UPDATE", (rs, row) -> rs.getInt(1)));
        assertThrows(CnnWriteRejectedException.class, () -> client.execute("SELECT id FROM orders; DELETE FROM orders"));
        assertEquals(1L, client.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }

    @Test
    void unDroitAutreQueSelectEstSignale() {
        assertTrue(CnnGrantVerifier.extraPrivileges("GRANT SELECT ON `colis`.* TO 'lecteur'@'%'").isEmpty());
        assertTrue(CnnGrantVerifier.extraPrivileges("GRANT USAGE ON *.* TO 'lecteur'@'%'").isEmpty());
        assertTrue(CnnGrantVerifier.extraPrivileges("GRANT SELECT, SHOW VIEW ON `colis`.* TO 'lecteur'@'%'").isEmpty());
        assertEquals(List.of("INSERT", "UPDATE", "DELETE"),
                CnnGrantVerifier.extraPrivileges("GRANT SELECT, INSERT, UPDATE, DELETE ON `colis`.* TO 'lecteur'@'%'"));
        assertEquals(List.of("ALL PRIVILEGES"),
                CnnGrantVerifier.extraPrivileges("GRANT ALL PRIVILEGES ON *.* TO 'lecteur'@'%'"));
    }

    @Test
    void lePoolEstEnLectureSeule() {
        CnnProperties properties = new CnnProperties();
        properties.getDb().setUrl("jdbc:mysql://127.0.0.1:1/colisnangai_db");
        properties.getDb().setUsername("lecteur");
        properties.getDb().setPassword("secret-de-test");
        try (var source = new CnnDataSourceConfig().cnnDataSource(properties)) {
            assertTrue(source.isReadOnly());
            assertEquals(3, source.getMaximumPoolSize());
            assertEquals(5_000L, source.getConnectionTimeout());
            assertEquals(-1L, source.getInitializationFailTimeout());
            assertEquals(20_000L, source.getLeakDetectionThreshold());
            assertEquals("SET SESSION TRANSACTION READ ONLY", source.getConnectionInitSql());
        }
    }

    @Test
    void aucuneColonnePersonnelleNEstSelectionnee() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/mindsetalliance/core/cnn/CnnDatabaseReader.java"));
        Matcher matcher = SQL.matcher(src);
        assertTrue(matcher.find());
        StringBuilder sql = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            sql.append(matcher.group()).append('\n');
        }
        String lowered = sql.toString().toLowerCase();
        for (String column : PERSONAL) {
            assertFalse(lowered.contains(column), column);
        }
        assertFalse(lowered.contains("select *"));
    }
}
