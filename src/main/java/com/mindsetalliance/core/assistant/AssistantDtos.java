package com.mindsetalliance.core.assistant;

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
            @NotBlank @Size(max = 2000) String message,
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
            String language
    ) {
        public ChatResponse withConversation(Long id) {
            return new ChatResponse(answer, facts, unavailable, unavailableReason, id, language);
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

    public record StoredMessage(
            String id,
            String role,
            String content,
            Instant at,
            List<FactCard> facts,
            boolean unavailable
    ) {
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
