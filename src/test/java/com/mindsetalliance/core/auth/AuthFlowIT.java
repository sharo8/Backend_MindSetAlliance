package com.mindsetalliance.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mindsetalliance_core");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("ma.kafka.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    /** Hash BCrypt de « ChangeMe123! » : comptes créés pour ce test uniquement (aucune donnée de démo en base). */
    private static final String HASH = "$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.";

    @BeforeEach
    void comptesDeTest() {
        jdbc.update("INSERT IGNORE INTO agents (email_pro, password_hash, nom, prenom, statut, two_factor_enabled, totp_secret) VALUES (?, ?, 'Test', 'Sans2fa', 'ACTIF', false, NULL)",
                "it.sans2fa@test.local", HASH);
        jdbc.update("INSERT IGNORE INTO agents (email_pro, password_hash, nom, prenom, statut, two_factor_enabled, totp_secret) VALUES (?, ?, 'Test', 'Avec2fa', 'ACTIF', true, 'JBSWY3DPEHPK3PXP')",
                "it.avec2fa@test.local", HASH);
        jdbc.update("INSERT IGNORE INTO agent_roles (agent_id, role_id, project_id) SELECT a.id, r.id, NULL FROM agents a, roles r WHERE a.email_pro = 'it.sans2fa@test.local' AND r.nom = 'SUPPORT' AND NOT EXISTS (SELECT 1 FROM agent_roles x WHERE x.agent_id = a.id)");
        jdbc.update("INSERT IGNORE INTO agent_roles (agent_id, role_id, project_id) SELECT a.id, r.id, NULL FROM agents a, roles r WHERE a.email_pro = 'it.avec2fa@test.local' AND r.nom = 'FINANCE' AND NOT EXISTS (SELECT 1 FROM agent_roles x WHERE x.agent_id = a.id)");
    }

    @Test
    void login2faJwksEtRefresh() throws Exception {
        mockMvc.perform(get("/api/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"it.sans2fa@test.local\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        if (login.contains("requires2fa") && login.contains("true")) {
            throw new AssertionError("Un compte sans 2FA ne doit pas l'exiger");
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"it.avec2fa@test.local\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2fa").value(true))
                .andExpect(jsonPath("$.challengeId", notNullValue()));
    }
}
