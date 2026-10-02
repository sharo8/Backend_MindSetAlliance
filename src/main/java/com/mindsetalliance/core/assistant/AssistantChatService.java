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
            Data cards and tables are rendered automatically under your reply, already translated:
            never repeat their rows as a markdown table or a bullet list; answer in one to three sentences
            with the key figures only (use **bold** for the main number or name).
            """;

    private static final String LANGUAGE_RULE = """
            REPLY LANGUAGE: %1$s.
            The administrator's latest message is written in %1$s. Write your entire reply in %1$s:
            sentences, headings, list labels and any column or card names you mention.
            Earlier messages of this conversation may use another language: ignore their language,
            only the latest message decides.
            Tables and cards returned by the tools are shown under your reply: do not list their rows.""";

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
        if (!properties.configured()) {
            return persist(request, unavailable("missing_key", lang));
        }
        try {
            List<JsonNode> messages = seedMessages(request, lang);
            List<FactCard> facts = new ArrayList<>();
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
                    return persist(request, new ChatResponse(
                            withoutDuplicatedRows(answer, facts), facts, false, null, null, lang.code()));
                }
                messages.add(choice);
                for (JsonNode call : toolCalls) {
                    String name = call.path("function").path("name").asText();
                    JsonNode args = parseArgs(call.path("function").path("arguments").asText("{}"));
                    AssistantToolsService.ToolOutcome outcome = tools.execute(name, args);
                    if (outcome.fact() != null) {
                        facts.add(AssistantLabels.render(outcome.fact(), lang));
                    }
                    ObjectNode toolMsg = mapper.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", call.path("id").asText());
                    toolMsg.put("content", mapper.writeValueAsString(outcome.payload()));
                    messages.add(toolMsg);
                }
            }
            return persist(request, new ChatResponse(
                    localized(lang,
                            "La question a nécessité trop d’étapes. Simplifiez-la.",
                            "The question required too many steps. Please simplify it.",
                            "Motuna yango esengaki makambo mingi koleka. Pesa yango na pete.",
                            "Swali lilihitaji hatua nyingi mno. Tafadhali lirahisishe."),
                    facts,
                    false,
                    null,
                    null,
                    lang.code()
            ));
        } catch (GroqCallException ex) {
            log.warn("Assistant Groq {} HTTP {} {}", ex.reasonCode(), ex.httpStatus(), abbreviate(ex.groqBody()));
            return persist(request, unavailable(ex.reasonCode(), lang));
        } catch (BusinessException ex) {
            if (ex.getStatus() == 503) {
                return persist(request, unavailable("network", lang));
            }
            throw ex;
        } catch (Exception ex) {
            log.warn("Assistant: {}", ex.getMessage());
            return persist(request, unavailable("internal", lang));
        }
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

    private static String localized(AssistantLanguage lang, String fr, String en, String ln, String sw) {
        return switch (lang) {
            case EN -> en;
            case LN -> ln;
            case SW -> sw;
            default -> fr;
        };
    }

    private static ChatResponse unavailable(String reason, AssistantLanguage lang) {
        String answer = switch (reason) {
            case "missing_key" -> "Assistant IA non configuré — contactez l’administrateur système.";
            case "invalid_key" -> "Clé API Groq invalide — vérifiez GROQ_API_KEY.";
            case "rate_limited", "network", "groq_error" -> "Erreur temporaire, réessayez dans quelques instants.";
            default -> "Erreur temporaire, réessayez dans quelques instants.";
        };
        return new ChatResponse(answer, List.of(), true, reason, null, lang.code());
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
            Long id = conversations.appendTurn(
                    request.conversationId(),
                    request.message(),
                    response.answer(),
                    response.facts(),
                    response.unavailable()
            );
            return response.withConversation(id);
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

    private List<JsonNode> seedMessages(ChatRequest request, AssistantLanguage lang) {
        List<JsonNode> messages = new ArrayList<>();
        String languageRule = LANGUAGE_RULE.formatted(lang.promptName());
        ObjectNode system = mapper.createObjectNode();
        system.put("role", "system");
        system.put("content", SYSTEM + "\n" + languageRule);
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
        reminder.put("content", languageRule);
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
