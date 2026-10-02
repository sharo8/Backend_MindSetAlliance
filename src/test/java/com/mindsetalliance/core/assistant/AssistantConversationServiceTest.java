package com.mindsetalliance.core.assistant;

import com.mindsetalliance.core.assistant.AssistantDtos.ChatRequest;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationDetail;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationPage;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "ma.kafka.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Transactional
class AssistantConversationServiceTest {

    @Autowired
    AssistantConversationService conversations;
    @Autowired
    AssistantChatService chatService;
    @Autowired
    AgentRepository agentRepository;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistReloadAndIsolation() {
        List<Agent> agents = agentRepository.findAll();
        assertTrue(agents.size() >= 2, "Il faut au moins deux agents en base");
        Agent owner = agents.get(0);
        Agent other = agents.stream().filter(a -> !a.getId().equals(owner.getId())).findFirst().orElseThrow();

        authenticate(owner);
        String marker = "Combien d’utilisateurs actifs avons-nous ? HIST-" + Instant.now().toEpochMilli();
        var response = chatService.chat(new ChatRequest(marker, List.of(), null));
        Long conversationId = response.conversationId();
        org.junit.jupiter.api.Assertions.assertNotNull(conversationId);

        ConversationPage page = conversations.list(marker, 0, 20);
        assertEquals(1, page.items().size());
        assertEquals(conversationId, page.items().getFirst().id());
        assertTrue(page.items().getFirst().title().startsWith("Combien"));

        ConversationDetail detail = conversations.getOwn(conversationId);
        assertEquals(2, detail.messages().size());
        assertEquals("user", detail.messages().get(0).role());
        assertEquals(marker, detail.messages().get(0).content());
        assertEquals("assistant", detail.messages().get(1).role());
        assertFalse(detail.messages().get(1).content().isBlank());

        authenticate(other);
        ConversationPage otherPage = conversations.list(marker, 0, 20);
        assertTrue(otherPage.items().stream().noneMatch(item -> conversationId.equals(item.id())));
        assertThrows(Exception.class, () -> conversations.getOwn(conversationId));
    }

    private void authenticate(Agent agent) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("agent-id-" + agent.getId())
                .claim("roles", List.of(Map.of("role", "ADMIN_SYSTEME")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
