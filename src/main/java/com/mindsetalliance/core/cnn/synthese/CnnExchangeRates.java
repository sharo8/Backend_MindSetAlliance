package com.mindsetalliance.core.cnn.synthese;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Taux de change du jour, depuis la même source publique que l'application Colis na Nga
 * (open.er-api.com, mise en cache 6 h). En cas d'échec : null, jamais de taux inventé.
 */
@Component
public class CnnExchangeRates {

    private static final Logger log = LoggerFactory.getLogger(CnnExchangeRates.class);
    private static final String URL = "https://open.er-api.com/v6/latest/USD";
    private static final Duration TTL = Duration.ofHours(6);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(10);

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Map<String, Object> cached;
    private volatile Instant nextRefresh = Instant.EPOCH;

    public CnnExchangeRates() {
        Duration timeout = Duration.ofSeconds(5);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public Map<String, Object> current() {
        if (Instant.now().isBefore(nextRefresh)) {
            return cached;
        }
        synchronized (this) {
            if (Instant.now().isBefore(nextRefresh)) {
                return cached;
            }
            try {
                String body = client.get().uri(URL).retrieve().body(String.class);
                JsonNode rates = mapper.readTree(body).path("rates");
                if (!rates.has("CDF")) {
                    throw new IllegalStateException("Réponse sans taux CDF");
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("usdCdf", rates.path("CDF").decimalValue());
                m.put("usdRwf", rates.has("RWF") ? rates.path("RWF").decimalValue() : null);
                m.put("majLe", OffsetDateTime.now(CnnSyntheseService.KINSHASA).toString());
                m.put("source", "open.er-api.com");
                cached = m;
                nextRefresh = Instant.now().plus(TTL);
            } catch (Exception e) {
                log.warn("Taux de change indisponibles : {}", e.getMessage());
                nextRefresh = Instant.now().plus(RETRY_AFTER_FAILURE);
            }
            return cached;
        }
    }
}
