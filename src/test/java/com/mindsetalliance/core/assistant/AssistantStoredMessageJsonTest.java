package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.assistant.AssistantDtos.StoredMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantStoredMessageJsonTest {

    @Test
    void lesAnciensMessagesRestentLisibles() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String raw = """
                [{"id":"1","role":"assistant","content":"ok","at":"2026-10-11T20:00:00Z","facts":[],"unavailable":false}]
                """;
        List<StoredMessage> messages = mapper.readValue(raw, new TypeReference<>() {
        });
        assertEquals("ok", messages.getFirst().content());
        assertNull(messages.getFirst().tools());
        assertNull(messages.getFirst().feedback());
    }

    @Test
    void leTitreSeCoupeALaLimiteDunMot() {
        String title = AssistantConversationService.titleFrom("hey Combien d utilisateurs actifs avons nous vraiment dans le systeme de gestion");
        assertTrue(title.length() <= 48);
        assertTrue(title.startsWith("Combien"));
        assertEquals("Conversation", AssistantConversationService.titleFrom("hey"));
        assertEquals("Conversation", AssistantConversationService.titleFrom("   "));
    }
}
