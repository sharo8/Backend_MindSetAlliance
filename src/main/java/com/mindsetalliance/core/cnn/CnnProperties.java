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
    private Api api = new Api();
    /** Fuseau dans lequel le backend Colis na Nga enregistre ses dates (Cloud Run : UTC). */
    private String storageZone = "UTC";

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

    public String getStorageZone() {
        return storageZone;
    }

    public void setStorageZone(String storageZone) {
        this.storageZone = storageZone == null || storageZone.isBlank() ? "UTC" : storageZone;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api == null ? new Api() : api;
    }

    /** Backend Colis na Nga pour le relais d'administration (écritures via ses propres règles métier). */
    public static class Api {
        private String baseUrl = "";
        private int timeoutSeconds = 20;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
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
