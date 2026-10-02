package com.mindsetalliance.core.integration.cnn;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Client HTTP unique vers Colis na Nga.
 *
 * Rapidité : un seul {@link HttpClient} partagé, donc connexions TCP/TLS réutilisées
 * (keep-alive) au lieu d'une poignée de main par requête ; délais courts pour échouer
 * vite ; petit cache des GET pour absorber les rafraîchissements répétés de la console.
 */
@Component
public class CnnClient {

    public record CnnResponse(int status, byte[] body, String contentType) {
        boolean ok() { return status >= 200 && status < 300; }
    }

    public static class CnnUnavailableException extends RuntimeException {
        private final boolean timeout;
        CnnUnavailableException(String message, Throwable cause, boolean timeout) {
            super(message, cause);
            this.timeout = timeout;
        }
        public boolean isTimeout() { return timeout; }
    }

    private record CacheEntry(CnnResponse response, long expiresAt) {}

    private final CnnIntegrationProperties properties;
    private final HttpClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CnnClient(CnnIntegrationProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * @param cacheKey clé de cache (null = pas de cache) ; doit inclure tout ce qui change
     *                 la réponse, en particulier les rôles de l'appelant.
     */
    public CnnResponse get(String pathAndQuery, String bearer, String cacheKey) {
        long now = System.currentTimeMillis();
        if (cacheKey != null && properties.getCacheSeconds() > 0) {
            CacheEntry hit = cache.get(cacheKey);
            if (hit != null && hit.expiresAt() > now) {
                return hit.response();
            }
        }
        CnnResponse response = send(request(pathAndQuery, bearer).GET().build());
        if (cacheKey != null && properties.getCacheSeconds() > 0 && response.ok()) {
            if (cache.size() > 2_000) {
                cache.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
            }
            cache.put(cacheKey, new CacheEntry(response, now + properties.getCacheSeconds() * 1000L));
        }
        return response;
    }

    public CnnResponse post(String pathAndQuery, String bearer, byte[] body) {
        CnnResponse response = send(request(pathAndQuery, bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body))
                .build());
        // Une écriture peut rendre obsolètes les lectures en cache.
        cache.clear();
        return response;
    }

    /** Ping léger pour garder Colis na Nga éveillé ; les erreurs sont ignorées. */
    public void ping() {
        try {
            http.send(HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/actuator/health"))
                    .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private HttpRequest.Builder request(String pathAndQuery, String bearer) {
        return HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + pathAndQuery))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearer);
    }

    private CnnResponse send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("application/json");
            return new CnnResponse(response.statusCode(), response.body(), contentType);
        } catch (HttpTimeoutException e) {
            throw new CnnUnavailableException("Colis na Nga ne répond pas à temps", e, true);
        } catch (IOException e) {
            throw new CnnUnavailableException("Colis na Nga est injoignable", e, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CnnUnavailableException("Appel à Colis na Nga interrompu", e, false);
        }
    }
}
