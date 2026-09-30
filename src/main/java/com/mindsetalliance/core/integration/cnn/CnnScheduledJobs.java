package com.mindsetalliance.core.integration.cnn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.vitrine.ProjectEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tâches de fond de l'intégration CNN.
 *
 * <ul>
 *   <li><b>Maintien à chaud</b> : un hébergeur gratuit endort Colis na Nga après ~15 min
 *       d'inactivité, et le premier appel met alors ~1 min. Un ping léger toutes les
 *       10 min garde les réponses de la console instantanées.</li>
 *   <li><b>Rattrapage</b> : chaque nuit, et 2 min après le démarrage, le Core lit les
 *       totaux officiels de CNN et corrige ses compteurs. Si un événement s'est perdu,
 *       la vitrine se répare d'elle-même.</li>
 * </ul>
 */
@Component
public class CnnScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(CnnScheduledJobs.class);

    /** Clés de vitrine_kpis que CNN renvoie dans {@code totaux} (mêmes noms que les compteurs d'événements). */
    private static final Map<String, String> TOTALS = Map.of(
            "courses_creees", "OPERATIONNEL",
            "courses_livrees", "OPERATIONNEL",
            "course_annulee", "OPERATIONNEL",
            "incidents", "OPERATIONNEL",
            "coursier_valide", "OPERATIONNEL",
            "abonnement_regle", "OPERATIONNEL",
            "abonnement_expire", "OPERATIONNEL",
            "abonnements_montant", "FINANCE");

    private final CnnIntegrationProperties properties;
    private final CnnClient client;
    private final CnnServiceTokenIssuer tokenIssuer;
    private final ProjectEventProcessor processor;
    private final ObjectMapper objectMapper;

    public CnnScheduledJobs(CnnIntegrationProperties properties, CnnClient client,
                            CnnServiceTokenIssuer tokenIssuer, ProjectEventProcessor processor,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.tokenIssuer = tokenIssuer;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${ma.integration.cnn.keep-warm-minutes:10}", timeUnit = TimeUnit.MINUTES,
            initialDelay = 1)
    public void keepWarm() {
        if (properties.isEnabled() && properties.isKeepWarm()) {
            client.ping();
        }
    }

    @Scheduled(cron = "${ma.integration.cnn.reconcile-cron:0 15 3 * * *}", zone = "Africa/Kinshasa")
    public void nightlyReconcile() {
        reconcile();
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reconcileAfterStartup() {
        java.util.concurrent.CompletableFuture.runAsync(this::reconcile,
                java.util.concurrent.CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES));
    }

    public void reconcile() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            CnnClient.CnnResponse response = client.get("/api/ma/kpis/resume", tokenIssuer.forSystem(), null);
            if (!response.ok()) {
                log.warn("Rattrapage CNN : réponse HTTP {}", response.status());
                return;
            }
            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object totaux = body.get("totaux");
            if (!(totaux instanceof Map<?, ?> map)) {
                log.warn("Rattrapage CNN : champ 'totaux' absent");
                return;
            }
            int corrected = 0;
            for (Map.Entry<String, String> entry : TOTALS.entrySet()) {
                Object value = map.get(entry.getKey());
                if (value != null) {
                    processor.upsert(CnnServiceTokenIssuer.PROJECT_CODE, entry.getValue(), entry.getKey(),
                            ProjectEventProcessor.number(value, 0));
                    corrected++;
                }
            }
            log.info("Rattrapage CNN terminé : {} indicateurs alignés sur la source", corrected);
        } catch (CnnClient.CnnUnavailableException e) {
            log.warn("Rattrapage CNN reporté : {}", e.getMessage());
        } catch (Exception e) {
            log.error("Rattrapage CNN en échec", e);
        }
    }
}
