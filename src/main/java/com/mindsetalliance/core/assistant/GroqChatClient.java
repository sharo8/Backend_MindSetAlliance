package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GroqChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);
    private static final Pattern RETRY_IN = Pattern.compile("try again in ([0-9]+(?:\\.[0-9]+)?)(ms|s)");
    private static final long MAX_RATE_LIMIT_WAIT_MS = 12_000;

    private final AssistantProperties properties;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public GroqChatClient(AssistantProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        int ms = Math.max(5, properties.getTimeoutSeconds()) * 1000;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(ms))
                .build();
    }

    public JsonNode complete(List<JsonNode> messages, ArrayNode tools) {
        if (!properties.configured()) {
            throw GroqCallException.missingKey();
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 900);
        body.set("messages", mapper.valueToTree(messages));
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        if (properties.getModel().startsWith("openai/gpt-oss")) {
            // Les jetons de raisonnement comptent dans le quota Groq (8 000 TPM en palier gratuit).
            body.put("reasoning_effort", "low");
        }
        try {
            return mapper.readTree(post(body, true));
        } catch (GroqCallException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            String groqBody = ex.getResponseBodyAsString();
            int code = ex.getStatusCode().value();
            log.warn("Groq HTTP {} {}", code, abbreviate(groqBody));
            throw new GroqCallException(code, groqBody, reasonFor(code), "Groq HTTP " + code);
        } catch (ResourceAccessException ex) {
            log.warn("Groq réseau/TLS: {}", ex.getMessage());
            throw new GroqCallException(0, ex.getMessage(), "network", "network");
        } catch (Exception ex) {
            log.warn("Réponse Groq illisible: {}", ex.getMessage());
            throw new GroqCallException(0, ex.getMessage(), "groq_error", "parse");
        }
    }

    private String post(ObjectNode body, boolean retryOnRateLimit) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            long waitMs = ex.getStatusCode().value() == 429 ? retryAfterMs(ex.getResponseBodyAsString()) : -1;
            if (!retryOnRateLimit || waitMs < 0 || waitMs > MAX_RATE_LIMIT_WAIT_MS) {
                throw ex;
            }
            log.info("Groq quota atteint, nouvelle tentative dans {} ms", waitMs);
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ex;
            }
            return post(body, false);
        }
    }

    /** Délai indiqué par Groq (« Please try again in 5.49s »), -1 si absent. */
    static long retryAfterMs(String groqBody) {
        if (groqBody == null) {
            return -1;
        }
        Matcher m = RETRY_IN.matcher(groqBody);
        if (!m.find()) {
            return -1;
        }
        double value = Double.parseDouble(m.group(1));
        double ms = "ms".equals(m.group(2)) ? value : value * 1000;
        return (long) Math.ceil(ms) + 250;
    }

    private static String reasonFor(int code) {
        if (code == 401 || code == 403) {
            return "invalid_key";
        }
        if (code == 429) {
            return "rate_limited";
        }
        return "groq_error";
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").strip();
        return compact.length() <= 400 ? compact : compact.substring(0, 400) + "…";
    }

    private static org.springframework.http.client.ClientHttpRequestFactory requestFactory(int readMs) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                SSLContext ctx = windowsRootContext();
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .sslContext(ctx)
                        .build();
                JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(Duration.ofMillis(readMs));
                log.info("Groq TLS: magasin Windows-ROOT");
                return factory;
            } catch (Exception ex) {
                log.warn("Windows-ROOT indisponible ({}). TLS JVM par défaut.", ex.getMessage());
            }
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(readMs);
        return factory;
    }

    private static SSLContext windowsRootContext() throws Exception {
        KeyStore store = KeyStore.getInstance("Windows-ROOT");
        store.load(null, null);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }
}
