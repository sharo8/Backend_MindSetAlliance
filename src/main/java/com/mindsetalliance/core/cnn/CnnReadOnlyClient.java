package com.mindsetalliance.core.cnn;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

/**
 * Seul passage vers la base Colis na Nga. SELECT, WITH et SHOW, paramètres liés.
 */
public final class CnnReadOnlyClient {

    private final JdbcTemplate jdbc;

    public CnnReadOnlyClient(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        CnnSql.requireRead(sql);
        return jdbc.queryForObject(sql, type, args);
    }

    public <T> T query(String sql, ResultSetExtractor<T> extractor, Object... args) {
        CnnSql.requireRead(sql);
        return jdbc.query(sql, extractor, args);
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        CnnSql.requireRead(sql);
        return jdbc.query(sql, mapper, args);
    }

    public void query(String sql, RowCallbackHandler handler) {
        CnnSql.requireRead(sql);
        jdbc.query(sql, handler);
    }

    /** Toute écriture est rejetée avant d'atteindre le pilote. */
    public void execute(String sql) {
        CnnSql.requireRead(sql);
        throw new CnnWriteRejectedException("Écriture refusée");
    }
}
