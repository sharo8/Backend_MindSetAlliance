package com.mindsetalliance.core.cnn;

import com.mindsetalliance.core.cnn.synthese.CnnStatistiquesService;
import com.mindsetalliance.core.cnn.synthese.CnnSyntheseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Garde la liaison avec Colis na Nga « chaude » pour que MA Workspace réponde vite :
 * connexions à la base ouvertes, instance Cloud Run du backend Colis na Nga réveillée
 * (sinon le premier appel attend son démarrage), synthèse et statistiques préparées.
 */
@Component
public class CnnWarmup {

    private static final Logger log = LoggerFactory.getLogger(CnnWarmup.class);

    private final CnnProperties properties;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ObjectProvider<CnnSyntheseService> synthese;
    private final ObjectProvider<CnnStatistiquesService> statistiques;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CnnWarmup(CnnProperties properties,
                     @Qualifier("cnnJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc,
                     ObjectProvider<CnnSyntheseService> synthese,
                     ObjectProvider<CnnStatistiquesService> statistiques) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.synthese = synthese;
        this.statistiques = statistiques;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(() -> {
            pingDatabase();
            pingApi();
            try {
                CnnSyntheseService s = synthese.getIfAvailable();
                if (s != null) s.synthese();
                CnnStatistiquesService st = statistiques.getIfAvailable();
                if (st != null && st.disponible()) st.statistiques(30);
                log.info("Colis na Nga : liaison prête (base, API, synthèse, statistiques)");
            } catch (Exception e) {
                log.warn("Colis na Nga : préchargement incomplet : {}", e.getMessage());
            }
        }, "cnn-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** Toutes les 4 minutes : avant que Cloud Run n'endorme l'instance (environ 15 min sans requête). */
    @Scheduled(initialDelay = 240_000, fixedDelay = 240_000)
    public void keepWarm() {
        pingDatabase();
        pingApi();
    }

    private void pingDatabase() {
        JdbcTemplate template = jdbc.getIfAvailable();
        if (template == null) return;
        try {
            template.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            log.debug("Colis na Nga : base injoignable pour le moment : {}", e.getMessage());
        }
    }

    private void pingApi() {
        if (properties.getApi() == null || !properties.getApi().configured()) return;
        try {
            String base = properties.getApi().getBaseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/emergency-calls/contact"))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            // Toute réponse (même 401) suffit : l'instance est réveillée.
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("Colis na Nga : API injoignable pour le moment : {}", e.getMessage());
        }
    }
}
