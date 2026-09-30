package com.mindsetalliance.core.integration.cnn;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Réglages de l'intégration Colis na Nga (préfixe {@code ma.integration.cnn}).
 * Les secrets viennent de l'environnement, jamais du dépôt.
 */
@Component
@ConfigurationProperties(prefix = "ma.integration.cnn")
public class CnnIntegrationProperties {

    /** Active le relais /api/cnn/**, le maintien à chaud et le rattrapage nocturne. */
    private boolean enabled = false;
    /** URL de base du backend Colis na Nga, sans slash final. */
    private String baseUrl = "http://localhost:8080";
    /** Valeur du claim {@code aud} des jetons de service, vérifiée par Colis na Nga. */
    private String audience = "colis-na-nga";
    /** Durée de vie des jetons de service émis pour chaque appel relayé. */
    private int serviceTokenSeconds = 60;
    /** Secret HMAC partagé pour signer les événements poussés par Colis na Nga. */
    private String webhookSecret = "";
    /** Fenêtre anti-rejeu : écart maximal toléré entre l'horodatage signé et l'heure du Core. */
    private int webhookToleranceSeconds = 300;
    /** Cache des lectures GET relayées (0 = désactivé). */
    private int cacheSeconds = 10;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 20000;
    /** Ping périodique pour éviter la mise en veille de Colis na Nga (hébergeurs gratuits). */
    private boolean keepWarm = true;
    private int keepWarmMinutes = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", ""); }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public int getServiceTokenSeconds() { return serviceTokenSeconds; }
    public void setServiceTokenSeconds(int serviceTokenSeconds) { this.serviceTokenSeconds = serviceTokenSeconds; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public int getWebhookToleranceSeconds() { return webhookToleranceSeconds; }
    public void setWebhookToleranceSeconds(int webhookToleranceSeconds) { this.webhookToleranceSeconds = webhookToleranceSeconds; }
    public int getCacheSeconds() { return cacheSeconds; }
    public void setCacheSeconds(int cacheSeconds) { this.cacheSeconds = cacheSeconds; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public boolean isKeepWarm() { return keepWarm; }
    public void setKeepWarm(boolean keepWarm) { this.keepWarm = keepWarm; }
    public int getKeepWarmMinutes() { return keepWarmMinutes; }
    public void setKeepWarmMinutes(int keepWarmMinutes) { this.keepWarmMinutes = keepWarmMinutes; }
}
