package com.mindsetalliance.core.auth;

import org.junit.jupiter.api.Test;
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

    @Test
    void login2faJwksEtRefresh() throws Exception {
        mockMvc.perform(get("/api/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"support.cnn@mindsetalliance.cd\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        if (login.contains("requires2fa") && login.contains("true")) {
            throw new AssertionError("Le support CNN ne doit pas exiger la 2FA");
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"finance@mindsetalliance.cd\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2fa").value(true))
                .andExpect(jsonPath("$.challengeId", notNullValue()));
    }
}
