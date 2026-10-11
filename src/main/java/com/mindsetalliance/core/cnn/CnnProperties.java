package com.mindsetalliance.core.cnn;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lecture Colis na Nga. {@code mode=api} (défaut) laisse le Core sur ses tables.
 * {@code mode=db} ouvre une source JDBC distincte, jamais utilisée par Hibernate ni Flyway.
 */
@ConfigurationProperties(prefix = "ma.cnn")
public class CnnProperties {

    private String mode = "api";
    private int cacheSeconds = 10;
    private Db db = new Db();

    public boolean dbMode() {
        return "db".equalsIgnoreCase(mode);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public Db getDb() {
        return db;
    }

    public void setDb(Db db) {
        this.db = db == null ? new Db() : db;
    }

    public static class Db {
        private String url = "";
        private String username = "";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
