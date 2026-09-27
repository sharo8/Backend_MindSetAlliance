package com.mindsetalliance.core.assistant;

import com.mindsetalliance.core.assistant.AssistantDtos.ChatTurn;
import com.mindsetalliance.core.assistant.AssistantDtos.FactCard;
import com.mindsetalliance.core.assistant.AssistantToolsService.FactDraft;
import com.mindsetalliance.core.assistant.AssistantToolsService.KpiDraft;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistantLanguageTest {

    @Test
    void detectsLanguageOfEachQuestion() {
        assertEquals(AssistantLanguage.EN, AssistantLanguage.detect("Who's the last person who login the last", List.of()));
        assertEquals(AssistantLanguage.EN, AssistantLanguage.detect("How many users?", List.of()));
        assertEquals(AssistantLanguage.EN, AssistantLanguage.detect("What permissions does the Support role have?", List.of()));
        assertEquals(AssistantLanguage.FR, AssistantLanguage.detect("Combien d'utilisateurs actifs ?", List.of()));
        assertEquals(AssistantLanguage.FR, AssistantLanguage.detect("Qui s'est connecté en dernier ?", List.of()));
        assertEquals(AssistantLanguage.LN, AssistantLanguage.detect("Bato boni bazali kosala lelo?", List.of()));
        assertEquals(AssistantLanguage.SW, AssistantLanguage.detect("Watumiaji wangapi wako hai leo?", List.of()));
    }

    @Test
    void ambiguousQuestionFollowsLatestIdentifiableQuestionNotTheFirst() {
        List<ChatTurn> history = List.of(
                new ChatTurn("user", "How many users?"),
                new ChatTurn("assistant", "There are 24 users."),
                new ChatTurn("user", "Combien de tickets ouverts ?"),
                new ChatTurn("assistant", "Il y a 5 tickets.")
        );
        assertEquals(AssistantLanguage.FR, AssistantLanguage.detect("CNN ?", history));
        assertEquals(AssistantLanguage.EN, AssistantLanguage.detect("Who logged in last?", history));
        assertEquals(AssistantLanguage.FR, AssistantLanguage.detect("CNN ?", List.of()));
    }

    @Test
    void factLabelsFollowQuestionLanguage() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("quand", "27/09 22:18");
        row.put("acteur", "Grace Kabila");
        row.put("action", "LOGIN");
        row.put("objet", "SESSION");
        FactDraft logins = new FactDraft("lastLogins", null, "table", List.of(row), List.of());

        FactCard en = AssistantLabels.render(logins, AssistantLanguage.EN);
        assertEquals("Latest logins", en.title());
        assertEquals(List.of("When", "Actor", "Action", "Object"), en.columns().stream().map(AssistantDtos.FactColumn::label).toList());
        assertEquals("action", en.columns().get(2).kind());

        FactCard fr = AssistantLabels.render(logins, AssistantLanguage.FR);
        assertEquals("Dernières connexions", fr.title());
        assertEquals(List.of("Quand", "Acteur", "Action", "Objet"), fr.columns().stream().map(AssistantDtos.FactColumn::label).toList());

        FactDraft staff = new FactDraft("staff", null, "kpis", List.of(), List.of(
                new KpiDraft("total", null, 24, "total"),
                new KpiDraft("active", null, 21, "positive"),
                new KpiDraft("inactive", null, 3, "negative"),
                new KpiDraft("filter", null, 24, "filter")));
        FactCard staffEn = AssistantLabels.render(staff, AssistantLanguage.EN);
        assertEquals("Workforce", staffEn.title());
        assertEquals(List.of("Total", "Active", "Inactive", "Filter"), staffEn.kpis().stream().map(k -> k.get("label")).toList());
        assertEquals("positive", staffEn.kpis().get(1).get("tone"));
    }

    @Test
    void parsesGroqRetryDelay() {
        assertEquals(5740, GroqChatClient.retryAfterMs("Please try again in 5.49s. Need more tokens?"));
        assertEquals(700, GroqChatClient.retryAfterMs("Please try again in 450ms."));
        assertEquals(-1, GroqChatClient.retryAfterMs("{\"error\":\"other\"}"));
    }

    @Test
    void frenchOnlyDatabaseLabelsAreDroppedOutsideFrench() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "VIEW_MAP");
        row.put("libelle", "Cartographie temps réel");
        row.put("module", "Opérations");
        FactDraft perms = new FactDraft("permissions", "SUPPORT", "table", List.of(row), List.of());

        FactCard en = AssistantLabels.render(perms, AssistantLanguage.EN);
        assertEquals("Permissions · SUPPORT", en.title());
        assertEquals(List.of("code", "module"), en.columns().stream().map(AssistantDtos.FactColumn::key).toList());
        assertEquals("Operations", en.rows().getFirst().get("module"));

        FactCard fr = AssistantLabels.render(perms, AssistantLanguage.FR);
        assertEquals(List.of("code", "libelle", "module"), fr.columns().stream().map(AssistantDtos.FactColumn::key).toList());
    }

    @Test
    void bulletsRepeatingTableRowsAreRemoved() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("quand", "27/09 21:51");
        row.put("acteur", "Grace Kabila");
        row.put("action", "LOGIN");
        row.put("objet", "SESSION");
        FactCard audit = AssistantLabels.render(
                new FactDraft("todayAudit", null, "table", List.of(row), List.of()), AssistantLanguage.FR);
        String answer = """
                Journal d'audit d'aujourd'hui

                - 21 h 51 – Grace Kabila – LOGIN – SESSION
                - Pensez à activer la 2FA pour tous les comptes.

                Total : **1 connexion** aujourd'hui.""";

        assertEquals("""
                Journal d'audit d'aujourd'hui

                - Pensez à activer la 2FA pour tous les comptes.

                Total : **1 connexion** aujourd'hui.""",
                AssistantChatService.withoutDuplicatedRows(answer, List.of(audit)));
        assertEquals(answer, AssistantChatService.withoutDuplicatedRows(answer, List.of()));
    }
}
