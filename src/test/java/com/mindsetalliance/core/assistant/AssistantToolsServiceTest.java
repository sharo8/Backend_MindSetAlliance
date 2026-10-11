package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "ma.kafka.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class AssistantToolsServiceTest {

    @Autowired
    AssistantToolsService tools;
    @Autowired
    AgentRepository agentRepository;
    @Autowired
    ObjectMapper mapper;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userCountMatchesDatabase() throws Exception {
        authenticateAsAdmin();

        long expectedActifs = agentRepository.findAll().stream()
                .filter(a -> "ACTIF".equalsIgnoreCase(a.getStatut()))
                .count();
        AssistantToolsService.ToolOutcome outcome = tools.execute("get_user_count", mapper.readTree("{\"statut\":\"ACTIF\"}"));
        assertNotNull(outcome.payload().get("actifs"));
        assertEquals(expectedActifs, ((Number) outcome.payload().get("actifs")).longValue());
        assertEquals(expectedActifs, ((Number) outcome.payload().get("resultatFiltre")).longValue());
    }

    /**
     * Pas de {@code @Transactional} sur le test : la session du repository se ferme après findAll.
     * Les outils doivent donc utiliser des counts / JOIN FETCH et ne jamais toucher un proxy LAZY.
     */
    @Test
    void ticketStatsAndSystemSummaryDoNotTriggerLazyInitialization() throws Exception {
        authenticateAsAdmin();

        AssistantToolsService.ToolOutcome tickets = assertDoesNotThrow(
                () -> tools.execute("get_ticket_stats", mapper.readTree("{}")));
        assertNotNull(tickets.payload().get("total"));
        assertNotNull(tickets.payload().get("parStatut"));
        assertFalse(tickets.payload().containsKey("error"));

        AssistantToolsService.ToolOutcome cnn = assertDoesNotThrow(
                () -> tools.execute("get_cnn_status", mapper.readTree("{}")));
        assertTrue(cnn.payload().containsKey("ticketsCnn"));

        AssistantToolsService.ToolOutcome summary = assertDoesNotThrow(
                () -> tools.execute("get_system_summary", mapper.readTree("{}")));
        assertNotNull(summary.payload().get("utilisateursTotal"));
        assertNotNull(summary.payload().get("utilisateursActifs"));
        assertNotNull(summary.payload().get("utilisateursInactifs"));
        assertNotNull(summary.payload().get("ticketsOuvertsParPriorite"));
        assertNotNull(summary.payload().get("notificationsNonLues"));
        assertNotNull(summary.payload().get("departements"));
        // DTO only — no JPA entity leaked to the model context
        summary.payload().values().forEach(v -> assertFalse(v != null && v.getClass().getName().contains(".entity."),
                () -> "unexpected entity in payload: " + v.getClass()));
    }

    private void authenticateAsAdmin() {
        Agent admin = agentRepository.findByEmailProIgnoreCase("alinekabacele@gmail.com")
                .orElseGet(() -> agentRepository.findAll().stream().findFirst().orElseThrow());
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("agent-id-" + admin.getId())
                .claim("roles", List.of(Map.of("role", "ADMIN_SYSTEME")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
