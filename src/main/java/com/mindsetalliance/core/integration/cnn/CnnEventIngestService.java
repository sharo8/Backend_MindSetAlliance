package com.mindsetalliance.core.integration.cnn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.vitrine.ProjectEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Réception des événements poussés par Colis na Nga (remplace Kafka, même format JSON).
 *
 * Garanties :
 * <ul>
 *   <li><b>Authenticité et intégrité</b> : HMAC-SHA256 du corps brut avec un secret partagé ;</li>
 *   <li><b>Anti-rejeu</b> : l'horodatage signé doit être à moins de 5 min de l'heure du Core ;</li>
 *   <li><b>Exactement une fois</b> : chaque {@code eventId} est mémorisé dans
 *       {@code integration_inbox} dans la même transaction que la mise à jour des KPI ;</li>
 *   <li><b>Pas de perte</b> : un événement en échec est renvoyé dans {@code failed},
 *       l'émetteur le réessaie plus tard.</li>
 * </ul>
 */
@Service
public class CnnEventIngestService {

    private static final Logger log = LoggerFactory.getLogger(CnnEventIngestService.class);
    private static final Set<String> ALLOWED_TOPICS =
            Set.of(ProjectEventProcessor.TOPIC_COURSES, ProjectEventProcessor.TOPIC_COURSIERS);
    private static final int MAX_BATCH = 500;

    public record IngestResult(List<String> accepted, List<String> duplicates, List<String> failed) {}

    public static class InvalidSignatureException extends RuntimeException {
        InvalidSignatureException(String message) { super(message); }
    }

    private final CnnIntegrationProperties properties;
    private final ProjectEventProcessor processor;
    private final InboxEventRepository inboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public CnnEventIngestService(CnnIntegrationProperties properties, ProjectEventProcessor processor,
                                 InboxEventRepository inboxRepository, TransactionTemplate transactionTemplate,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.processor = processor;
        this.inboxRepository = inboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public IngestResult ingest(String timestampHeader, String signatureHeader, byte[] rawBody) {
        verifySignature(timestampHeader, signatureHeader, rawBody);

        List<Map<String, Object>> events;
        try {
            Map<String, Object> envelope = objectMapper.readValue(rawBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) envelope.get("events");
            events = list == null ? List.of() : list;
        } catch (Exception e) {
            throw new IllegalArgumentException("Corps JSON invalide : attendu {\"events\": [...]}");
        }
        if (events.size() > MAX_BATCH) {
            throw new IllegalArgumentException("Lot trop grand (max " + MAX_BATCH + " événements)");
        }

        List<String> accepted = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Map<String, Object> event : events) {
            String eventId = event.get("eventId") == null ? null : String.valueOf(event.get("eventId"));
            String topic = String.valueOf(event.get("topic"));
            Object payload = event.get("payload");
            if (eventId == null || eventId.isBlank() || eventId.length() > 64
                    || !ALLOWED_TOPICS.contains(topic) || !(payload instanceof Map<?, ?>)) {
                // Événement malformé : le réessayer ne changera rien, on l'acquitte et on le journalise.
                log.warn("Événement CNN ignoré (malformé) : eventId={}, topic={}", eventId, topic);
                if (eventId != null) {
                    duplicates.add(eventId);
                }
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) payload;
            // Le projet est imposé par le canal : CNN ne peut pas alimenter les KPI d'un autre projet.
            body.put("projectCode", CnnServiceTokenIssuer.PROJECT_CODE);
            try {
                Boolean isNew = transactionTemplate.execute(status -> {
                    if (inboxRepository.existsById(eventId)) {
                        return false;
                    }
                    processor.process(topic, body);
                    String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
                    inboxRepository.saveAndFlush(new InboxEvent(eventId, "CNN", topic,
                            type == null ? null : type.substring(0, Math.min(60, type.length()))));
                    return true;
                });
                (Boolean.TRUE.equals(isNew) ? accepted : duplicates).add(eventId);
            } catch (DataIntegrityViolationException e) {
                // Livraison concurrente du même événement : l'autre transaction l'a déjà compté.
                duplicates.add(eventId);
            } catch (RuntimeException e) {
                log.error("Échec du traitement de l'événement CNN {} : {}", eventId, e.getMessage());
                failed.add(eventId);
            }
        }
        return new IngestResult(accepted, duplicates, failed);
    }

    private void verifySignature(String timestampHeader, String signatureHeader, byte[] rawBody) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.length() < 32) {
            // Jamais de mode « sans signature » : un secret absent ou faible bloque tout.
            throw new InvalidSignatureException("Secret d'intégration CNN non configuré");
        }
        if (timestampHeader == null || signatureHeader == null) {
            throw new InvalidSignatureException("Signature manquante");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new InvalidSignatureException("Horodatage invalide");
        }
        long drift = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (drift > properties.getWebhookToleranceSeconds()) {
            throw new InvalidSignatureException("Horodatage hors fenêtre (rejeu ou horloge décalée)");
        }
        String expected = "sha256=" + hmacHex(secret, timestampHeader.trim(), rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidSignatureException("Signature invalide");
        }
    }

    static String hmacHex(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponible", e);
        }
    }

    /** Les identifiants de plus de 30 jours ne servent plus au dédoublonnage. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Africa/Kinshasa")
    public void purgeOldInbox() {
        int removed = inboxRepository.purgeBefore(Instant.now().minus(30, ChronoUnit.DAYS));
        if (removed > 0) {
            log.info("integration_inbox : {} entrées purgées", removed);
        }
    }
}
