package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindsetalliance.core.assistant.AssistantDtos.ChatRequest;
import com.mindsetalliance.core.assistant.AssistantDtos.ChatResponse;
import com.mindsetalliance.core.assistant.AssistantDtos.ChatTurn;
import com.mindsetalliance.core.assistant.AssistantDtos.FactCard;
import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssistantChatService {

    private static final Logger log = LoggerFactory.getLogger(AssistantChatService.class);
    private static final String SYSTEM = """
            You are the internal assistant of MA Workspace (Mindset Alliance, Kinshasa). Be concise and factual.
            NEVER invent a number: call a tool for any data (staff, roles, access, audit, tickets, Colis na Nga).
            If a tool denies access, say so clearly.
            For business advice, rely first on get_ticket_stats or get_cnn_status when relevant.
            Quote the numbers returned by the tools. Never mention the technical names of the tools.
            Tool results use French field names and values (e.g. actifs, connexions, quand, acteur, ENTREPRISE):
            translate them into the reply language, never copy French words into a non-French reply.

            CONCISENESS (critical):
            Answer ONLY what was asked. For a count question (e.g. "how many inactive users?"), reply with
            1 short sentence containing the figure in **bold**, and nothing else — no totals, no other KPIs,
            no tables, no bullet lists of rows. Optionally add ONE short offer: "Would you like the list?"
            Add extra stats, lists or comparisons ONLY when the user explicitly asks (list, detail, compare).
            If the question is ambiguous, ask ONE short clarifying question instead of dumping data.
            When structured cards/tables are shown under your reply, do not rebuild them as markdown;
            never repeat their rows as a bullet list.
            """;

    private static final String LANGUAGE_RULE = """
            REPLY LANGUAGE: %1$s.
            The administrator's latest message is written in %1$s. Write your entire reply in %1$s:
            sentences, headings, list labels and any column or card names you mention.
            Earlier messages of this conversation may use another language: ignore their language,
            only the latest message decides.
            Tables and cards returned by the tools are shown under your reply: do not list their rows.""";

    private static final String INTENT_RULE = """
            DETECTED INTENT: %1$s.
            If INTENT is count: answer in at most 2 sentences with only the requested figure; do not mention other KPIs.
            If INTENT is list or detail: you may summarise and structured cards/tables may appear under the reply.
            If INTENT is summary: call get_system_summary and write a short 4–6 line overview from its numbers only.
            If INTENT is other: stay brief; do not invent extra statistics.""";

    private final GroqChatClient groq;
    private final AssistantToolsService tools;
    private final AssistantProperties properties;
    private final AuditService auditService;
    private final AssistantConversationService conversations;
    private final ObjectMapper mapper;

    public AssistantChatService(GroqChatClient groq,
                                AssistantToolsService tools,
                                AssistantProperties properties,
                                AuditService auditService,
                                AssistantConversationService conversations,
                                ObjectMapper mapper) {
        this.groq = groq;
        this.tools = tools;
        this.properties = properties;
        this.auditService = auditService;
        this.conversations = conversations;
        this.mapper = mapper;
    }

    public ChatResponse chat(ChatRequest request) {
        traceQuestion(request.message());
        AssistantLanguage lang = AssistantLanguage.detect(request.message(), request.history());
        AssistantIntent intent = AssistantIntent.detect(request.message());
        // Résumé système : chiffres via requêtes de comptage, sans appeler Groq (évite LazyInit + latence).
        if (intent == AssistantIntent.SUMMARY) {
            try {
                return persist(request, buildSystemSummaryResponse(lang, intent));
            } catch (AccessDeniedException ex) {
                log.warn("Assistant summary denied: {}", ex.getMessage());
                return persist(request, reply(localized(lang,
                                "Accès refusé pour le résumé système.",
                                "Access denied for the system summary.",
                                "Bokoti epekisami mpo na résumé système.",
                                "Ufikiaji umekataliwa kwa muhtasari wa mfumo."),
                        List.of(), false, null, lang, intent, List.of()));
            } catch (Exception ex) {
                log.error("Assistant summary failed", ex);
                return persist(request, unavailable("internal", lang, intent));
            }
        }
        if (!properties.configured()) {
            return persist(request, unavailable("missing_key", lang, intent));
        }
        try {
            List<JsonNode> messages = seedMessages(request, lang, intent);
            List<FactCard> facts = new ArrayList<>();
            List<String> toolNames = new ArrayList<>();
            for (int round = 0; round < 4; round++) {
                JsonNode completion = groq.complete(messages, toolCatalog());
                JsonNode choice = completion.path("choices").path(0).path("message");
                ArrayNode toolCalls = (ArrayNode) (choice.path("tool_calls").isArray() ? choice.get("tool_calls") : mapper.createArrayNode());
                if (toolCalls.isEmpty()) {
                    String answer = choice.path("content").asText("").trim();
                    if (answer.isBlank()) {
                        answer = localized(lang,
                                "Je n’ai pas pu formuler de réponse. Reformulez la question.",
                                "I could not formulate an answer. Please rephrase the question.",
                                "Nakokaki kopesa eyano te. Tuna motuna na ndenge mosusu.",
                                "Sikuweza kutoa jibu. Tafadhali uliza swali kwa njia nyingine.");
                    }
                    return persist(request, reply(
                            withoutDuplicatedRows(answer, facts), facts, false, null, lang, intent, toolNames));
                }
                messages.add(choice);
                for (JsonNode call : toolCalls) {
                    String name = call.path("function").path("name").asText();
                    if (!name.isBlank() && !toolNames.contains(name)) {
                        toolNames.add(name);
                    }
                    JsonNode args = parseArgs(call.path("function").path("arguments").asText("{}"));
                    AssistantToolsService.ToolOutcome outcome = tools.execute(name, args);
                    if (outcome.fact() != null && intent.includeFact(outcome.fact().kind())) {
                        facts.add(AssistantLabels.render(outcome.fact(), lang));
                    }
                    ObjectNode toolMsg = mapper.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", call.path("id").asText());
                    toolMsg.put("content", mapper.writeValueAsString(outcome.payload()));
                    messages.add(toolMsg);
                }
            }
            return persist(request, reply(
                    localized(lang,
                            "La question a nécessité trop d’étapes. Simplifiez-la.",
                            "The question required too many steps. Please simplify it.",
                            "Motuna yango esengaki makambo mingi koleka. Pesa yango na pete.",
                            "Swali lilihitaji hatua nyingi mno. Tafadhali lirahisishe."),
                    facts,
                    false,
                    null,
                    lang,
                    intent,
                    toolNames
            ));
        } catch (GroqCallException ex) {
            log.warn("Assistant Groq {} HTTP {} {}", ex.reasonCode(), ex.httpStatus(), abbreviate(ex.groqBody()));
            return persist(request, unavailable(ex.reasonCode(), lang, intent));
        } catch (BusinessException ex) {
            if (ex.getStatus() == 503) {
                return persist(request, unavailable("network", lang, intent));
            }
            throw ex;
        } catch (Exception ex) {
            log.error("Assistant unexpected failure", ex);
            return persist(request, unavailable("internal", lang, intent));
        }
    }

    private ChatResponse buildSystemSummaryResponse(AssistantLanguage lang, AssistantIntent intent) {
        AssistantToolsService.ToolOutcome outcome = tools.execute("get_system_summary", mapper.createObjectNode());
        if (outcome.payload().containsKey("error")) {
            throw new BusinessException(String.valueOf(outcome.payload().get("error")), 403);
        }
        List<FactCard> facts = new ArrayList<>();
        if (outcome.fact() != null && intent.includeFact(outcome.fact().kind())) {
            facts.add(AssistantLabels.render(outcome.fact(), lang));
        }
        return reply(formatSystemSummary(outcome.payload(), lang), facts, false, null, lang, intent, List.of("get_system_summary"));
    }

    static String formatSystemSummary(Map<String, Object> data, AssistantLanguage lang) {
        long total = asLong(data.get("utilisateursTotal"));
        long actifs = asLong(data.get("utilisateursActifs"));
        long inactifs = asLong(data.get("utilisateursInactifs"));
        long open = asLong(data.get("ticketsOuverts"));
        long notifs = asLong(data.get("notificationsNonLues"));
        long deps = asLong(data.get("departements"));
        Map<String, Long> byPriority = new LinkedHashMap<>();
        Object rawPriorities = data.get("ticketsOuvertsParPriorite");
        if (rawPriorities instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> byPriority.put(
                    k == null ? "?" : String.valueOf(k),
                    v instanceof Number n ? n.longValue() : 0L));
        }
        String priorities = byPriority.isEmpty()
                ? "—"
                : byPriority.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> priorityLabel(e.getKey(), lang) + " " + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
        return switch (lang) {
            case EN -> """
                    System overview:
                    - Users: **%d** total (**%d** active, **%d** inactive)
                    - Open tickets: **%d** (%s)
                    - Unread notifications: **%d**
                    - Departments: **%d**""".formatted(total, actifs, inactifs, open, priorities, notifs, deps);
            case LN -> """
                    Résumé ya système:
                    - Basali: **%d** nyonso (**%d** actifs, **%d** inactifs)
                    - Ba tickets oyo eza kofungwama: **%d** (%s)
                    - Ba notifications oyo otangi te: **%d**
                    - Ba départements: **%d**""".formatted(total, actifs, inactifs, open, priorities, notifs, deps);
            case SW -> """
                    Muhtasari wa mfumo:
                    - Watumiaji: **%d** jumla (**%d** hai, **%d** wasio hai)
                    - Tiketi zilizo wazi: **%d** (%s)
                    - Arifa ambazo hazijasomwa: **%d**
                    - Idara: **%d**""".formatted(total, actifs, inactifs, open, priorities, notifs, deps);
            default -> """
                    Résumé du système :
                    - Utilisateurs : **%d** au total (**%d** actifs, **%d** inactifs)
                    - Tickets ouverts : **%d** (%s)
                    - Notifications non lues : **%d**
                    - Départements : **%d**""".formatted(total, actifs, inactifs, open, priorities, notifs, deps);
        };
    }

    private static String priorityLabel(String raw, AssistantLanguage lang) {
        String key = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        return switch (key) {
            case "CRITIQUE", "HIGH" -> lang == AssistantLanguage.EN ? "Critical" : "Critique";
            case "ELEVEE", "MEDIUM" -> lang == AssistantLanguage.EN ? "High" : "Élevée";
            case "FAIBLE", "LOW" -> lang == AssistantLanguage.EN ? "Low" : "Faible";
            case "NORMALE", "NORMAL" -> lang == AssistantLanguage.EN ? "Normal" : "Normale";
            case "?", "" -> "—";
            default -> raw;
        };
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    /**
     * Le modèle recopie parfois les lignes des tableaux en liste à puces alors que le tableau est
     * déjà affiché : on retire les puces qui reprennent au moins deux valeurs d'une même ligne.
     */
    static String withoutDuplicatedRows(String answer, List<FactCard> facts) {
        List<List<String>> rowValues = facts.stream()
                .filter(f -> f.rows() != null)
                .flatMap(f -> f.rows().stream())
                .map(row -> row.values().stream()
                        .filter(v -> v != null && v.toString().trim().length() >= 3)
                        .map(v -> v.toString().trim().toLowerCase(Locale.ROOT))
                        .toList())
                .filter(values -> values.size() >= 2)
                .toList();
        if (rowValues.isEmpty()) {
            return answer;
        }
        StringBuilder kept = new StringBuilder();
        for (String line : answer.split("\\R", -1)) {
            String trimmed = line.trim();
            boolean bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
                    || trimmed.matches("^\\d+[.)]\\s.*");
            if (bullet) {
                String lower = trimmed.toLowerCase(Locale.ROOT);
                boolean duplicate = rowValues.stream()
                        .anyMatch(values -> values.stream().filter(lower::contains).count() >= 2);
                if (duplicate) {
                    continue;
                }
            }
            kept.append(line).append('\n');
        }
        String cleaned = kept.toString().replaceAll("\\n{3,}", "\n\n").trim();
        return cleaned.isBlank() ? answer : cleaned;
    }

    private static ChatResponse reply(String answer, List<FactCard> facts, boolean unavailable, String reason,
                                      AssistantLanguage lang, AssistantIntent intent, List<String> tools) {
        return new ChatResponse(answer, facts, unavailable, reason, null, lang.code(), intent.code(),
                tools == null ? List.of() : List.copyOf(tools), null);
    }

    private static String localized(AssistantLanguage lang, String fr, String en, String ln, String sw) {
        return switch (lang) {
            case EN -> en;
            case LN -> ln;
            case SW -> sw;
            default -> fr;
        };
    }

    private static ChatResponse unavailable(String reason, AssistantLanguage lang, AssistantIntent intent) {
        String answer = switch (reason) {
            case "missing_key" -> "Assistant IA non configuré — contactez l’administrateur système.";
            case "invalid_key" -> "Clé API Groq invalide — vérifiez GROQ_API_KEY.";
            case "rate_limited", "network", "groq_error" -> "Erreur temporaire, réessayez dans quelques instants.";
            default -> "Erreur temporaire, réessayez dans quelques instants.";
        };
        return reply(answer, List.of(), true, reason, lang, intent, List.of());
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").strip();
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "…";
    }

    private ChatResponse persist(ChatRequest request, ChatResponse response) {
        try {
            AssistantConversationService.AppendResult stored = conversations.appendTurn(
                    request.conversationId(),
                    request.message(),
                    response.answer(),
                    response.facts(),
                    response.unavailable(),
                    response.tools()
            );
            return response.withStored(stored.conversationId(), stored.messageId());
        } catch (Exception ex) {
            log.warn("Historique assistant: {}", ex.getMessage());
            return response;
        }
    }

    private void traceQuestion(String message) {
        String preview = message == null ? "" : message.strip();
        if (preview.length() > 180) {
            preview = preview.substring(0, 180);
        }
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("preview", preview);
        apres.put("length", message == null ? 0 : message.length());
        try {
            auditService.record(JwtRoles.agentId(), "ASK_ASSISTANT", "ASSISTANT", null, null, apres);
        } catch (Exception ex) {
            log.warn("Audit assistant: {}", ex.getMessage());
        }
    }

    private List<JsonNode> seedMessages(ChatRequest request, AssistantLanguage lang, AssistantIntent intent) {
        List<JsonNode> messages = new ArrayList<>();
        String languageRule = LANGUAGE_RULE.formatted(lang.promptName());
        String intentRule = INTENT_RULE.formatted(intent.code());
        ObjectNode system = mapper.createObjectNode();
        system.put("role", "system");
        system.put("content", SYSTEM + "\n" + languageRule + "\n" + intentRule);
        messages.add(system);
        List<ChatTurn> history = request.history() == null ? List.of() : request.history();
        int from = Math.max(0, history.size() - 6);
        for (ChatTurn turn : history.subList(from, history.size())) {
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "assistant" : "user";
            String content = turn.content();
            if (content.length() > 700) {
                content = content.substring(0, 700);
            }
            ObjectNode node = mapper.createObjectNode();
            node.put("role", role);
            node.put("content", content);
            messages.add(node);
        }
        // Rappel placé après l'historique : sinon le modèle suit la langue des tours précédents.
        ObjectNode reminder = mapper.createObjectNode();
        reminder.put("role", "system");
        reminder.put("content", languageRule + "\n" + intentRule);
        messages.add(reminder);
        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", request.message().strip());
        messages.add(user);
        return messages;
    }

    private JsonNode parseArgs(String raw) {
        try {
            return mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private ArrayNode toolCatalog() {
        ArrayNode tools = mapper.createArrayNode();
        tools.add(fn("get_user_count",
                "Effectifs réels : total, actifs, inactifs, éventuellement filtrés par statut ou rôle (FINANCE, SUPPORT, RH…).",
                Map.of(
                        "statut", Map.of("type", "string", "description", "ACTIF ou INACTIF"),
                        "role", Map.of("type", "string", "description", "Nom du rôle métier")
                )));
        tools.add(fn("get_role_permissions",
                "Liste les permissions du rôle demandé (matrice RBAC).",
                Map.of("role", Map.of("type", "string", "description", "Nom du rôle, ex. SUPPORT"))));
        tools.add(fn("get_permission_holders",
                "Qui détient une permission via un rôle (ex. MANAGE_USERS).",
                Map.of("permission", Map.of("type", "string", "description", "Code permission"))));
        tools.add(fn("get_company_wide_access",
                "Agents avec un rôle au périmètre entreprise (accès transverse).",
                Map.of()));
        tools.add(fn("get_access_deviations",
                "Dérogations GRANT et DENY actuellement enregistrées.",
                Map.of()));
        tools.add(fn("get_last_logins",
                "Dernières connexions journalisées (action LOGIN).",
                Map.of("limit", Map.of("type", "integer", "description", "Nombre max, défaut 8"))));
        tools.add(fn("get_today_audit",
                "Événements d'audit depuis minuit Kinshasa.",
                Map.of("limit", Map.of("type", "integer", "description", "Nombre max"))));
        tools.add(fn("get_ticket_stats",
                "Statistiques de tickets, éventuellement filtrées par code projet (CNN, MDR).",
                Map.of("projet", Map.of("type", "string", "description", "Code projet optionnel"))));
        tools.add(fn("get_cnn_status",
                "État d'intégration Colis na Nga + KPI démo Core + tickets CNN.",
                Map.of()));
        tools.add(fn("get_system_summary",
                "Résumé système : utilisateurs (total/actifs/inactifs), tickets ouverts par priorité, notifications non lues, départements. À utiliser pour summary / résumé / overview.",
                Map.of()));
        return tools;
    }

    private ObjectNode fn(String name, String description, Map<String, Map<String, String>> properties) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", name);
        function.put("description", description);
        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        List<String> required = new ArrayList<>();
        properties.forEach((key, meta) -> {
            ObjectNode p = props.putObject(key);
            String type = meta.getOrDefault("type", "string");
            boolean mandatory = ("role".equals(key) && "get_role_permissions".equals(name)) || "permission".equals(key);
            if (mandatory) {
                p.put("type", type);
                required.add(key);
            } else {
                // Groq valide strictement les arguments : certains modèles envoient null pour un filtre omis.
                p.putArray("type").add(type).add("null");
            }
            if (meta.get("description") != null) {
                p.put("description", meta.get("description"));
            }
        });
        if (!required.isEmpty()) {
            ArrayNode req = params.putArray("required");
            required.forEach(req::add);
        }
        return tool;
    }
}
