package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record ChatTurn(
            @Size(max = 20) String role,
            @Size(max = 1500) String content
    ) {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            List<ChatTurn> history,
            Long conversationId
    ) {
    }

    /** {@code kind} = "action" pour une colonne de codes d'action affichés en badge. */
    public record FactColumn(String key, String label, String kind) {
    }

    /** kpis : label, value, tone (total, positive, negative, filter, neutral). */
    public record FactCard(
            String title,
            String kind,
            List<Map<String, Object>> rows,
            List<Map<String, Object>> kpis,
            List<FactColumn> columns
    ) {
    }

    public record ChatResponse(
            String answer,
            List<FactCard> facts,
            boolean unavailable,
            String unavailableReason,
            Long conversationId,
            String language,
            /** count | list | detail | other — les cartes ne s'affichent que pour list/detail (et tables pour other). */
            String intent,
            /** Noms techniques des outils réellement appelés. Vide si aucun. */
            List<String> tools,
            /** Identifiant du message assistant enregistré, pour le retour pouce. */
            String messageId
    ) {
        public ChatResponse withStored(Long id, String storedMessageId) {
            return new ChatResponse(answer, facts, unavailable, unavailableReason, id, language, intent, tools, storedMessageId);
        }
    }

    public record StatusResponse(boolean configured, boolean available, String reason) {
        public static StatusResponse of(boolean configured) {
            if (configured) {
                return new StatusResponse(true, true, null);
            }
            return new StatusResponse(false, false, "missing_key");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoredMessage(
            String id,
            String role,
            String content,
            Instant at,
            List<FactCard> facts,
            boolean unavailable,
            List<String> tools,
            /** up, down, ou absent. */
            String feedback
    ) {
    }

    public record RenameRequest(@NotBlank @Size(max = 180) String title) {
    }

    public record FeedbackRequest(@NotBlank @Size(max = 40) String messageId, @Size(max = 8) String value) {
    }

    public record ConversationSummary(Long id, String title, Instant createdAt, Instant lastActivityAt) {
    }

    public record ConversationPage(List<ConversationSummary> items, long total, boolean hasMore) {
    }

    public record ConversationDetail(
            Long id,
            String title,
            Instant createdAt,
            Instant lastActivityAt,
            List<StoredMessage> messages
    ) {
    }
}
