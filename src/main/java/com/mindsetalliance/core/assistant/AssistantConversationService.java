package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationDetail;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationPage;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationSummary;
import com.mindsetalliance.core.assistant.AssistantDtos.FactCard;
import com.mindsetalliance.core.assistant.AssistantDtos.StoredMessage;
import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantConversationService {

    private static final Logger log = LoggerFactory.getLogger(AssistantConversationService.class);
    private static final int TITLE_MAX = 48;

    public record AppendResult(Long conversationId, String messageId) {
    }
    private static final TypeReference<List<StoredMessage>> MESSAGES = new TypeReference<>() {
    };

    private final AssistantConversationRepository repository;
    private final AgentRepository agentRepository;
    private final ObjectMapper mapper;
    private final AuditService auditService;

    public AssistantConversationService(AssistantConversationRepository repository,
                                        AgentRepository agentRepository,
                                        ObjectMapper mapper,
                                        AuditService auditService) {
        this.repository = repository;
        this.agentRepository = agentRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ConversationPage list(String query, int offset, int limit) {
        Long agentId = JwtRoles.agentId();
        int size = Math.min(Math.max(limit, 1), 50);
        int page = Math.max(offset, 0) / size;
        PageRequest pageable = PageRequest.of(page, size);
        String q = query == null ? "" : query.trim();
        Page<AssistantConversation> result = q.isBlank()
                ? repository.findByAgent_IdAndArchiveFalseOrderByLastActivityAtDesc(agentId, pageable)
                : repository.searchOwn(agentId, q, pageable);
        List<ConversationSummary> items = result.getContent().stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getLastActivityAt()))
                .toList();
        long total = result.getTotalElements();
        boolean hasMore = (long) page * size + items.size() < total;
        return new ConversationPage(items, total, hasMore);
    }

    @Transactional(readOnly = true)
    public ConversationDetail getOwn(Long id) {
        AssistantConversation conv = owned(id);
        return toDetail(conv);
    }

    @Transactional
    public void archiveOwn(Long id) {
        AssistantConversation conv = owned(id);
        conv.setArchive(true);
        conv.setLastActivityAt(Instant.now());
        repository.save(conv);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("archive", true);
        auditService.record(JwtRoles.agentId(), "DELETE", "ASSISTANT_CONVERSATION", id, null, apres);
    }

    @Transactional
    public ConversationSummary renameOwn(Long id, String title) {
        String clean = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        if (clean.isBlank() || clean.length() > 180) {
            throw new BusinessException("Titre invalide", 400);
        }
        AssistantConversation conv = owned(id);
        conv.setTitle(clean);
        conv.setLastActivityAt(Instant.now());
        repository.save(conv);
        return new ConversationSummary(conv.getId(), conv.getTitle(), conv.getCreatedAt(), conv.getLastActivityAt());
    }

    @Transactional
    public void feedbackOwn(Long id, String messageId, String value) {
        String stored = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!stored.isEmpty() && !stored.equals("up") && !stored.equals("down")) {
            throw new BusinessException("Retour invalide", 400);
        }
        AssistantConversation conv = owned(id);
        List<StoredMessage> messages = readMessages(conv);
        boolean found = false;
        List<StoredMessage> next = new ArrayList<>();
        for (StoredMessage message : messages) {
            if (messageId != null && messageId.equals(message.id()) && "assistant".equals(message.role())) {
                found = true;
                next.add(new StoredMessage(
                        message.id(), message.role(), message.content(), message.at(),
                        message.facts(), message.unavailable(), message.tools(),
                        stored.isEmpty() ? null : stored
                ));
            } else {
                next.add(message);
            }
        }
        if (!found) {
            throw new BusinessException("Message introuvable", 404);
        }
        writeMessages(conv, next);
        repository.save(conv);
    }

    @Transactional
    public AppendResult appendTurn(Long conversationId, String userText, String assistantText,
                                   List<FactCard> facts, boolean unavailable, List<String> tools) {
        Long agentId = JwtRoles.agentId();
        Instant now = Instant.now();
        AssistantConversation conv;
        if (conversationId == null) {
            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new BusinessException("Agent introuvable", 404));
            conv = new AssistantConversation();
            conv.setAgent(agent);
            conv.setTitle(titleFrom(userText));
            conv.setMessagesJson("[]");
            conv.setCreatedAt(now);
        } else {
            conv = owned(conversationId);
        }
        List<StoredMessage> messages = readMessages(conv);
        messages.add(new StoredMessage(UUID.randomUUID().toString(), "user", userText, now, List.of(), false, List.of(), null));
        String assistantId = UUID.randomUUID().toString();
        messages.add(new StoredMessage(
                assistantId,
                "assistant",
                assistantText,
                now.plusMillis(5),
                facts == null ? List.of() : facts,
                unavailable,
                tools == null ? List.of() : List.copyOf(tools),
                null
        ));
        writeMessages(conv, messages);
        conv.setLastActivityAt(now);
        return new AppendResult(repository.save(conv).getId(), assistantId);
    }

    private void writeMessages(AssistantConversation conv, List<StoredMessage> messages) {
        try {
            conv.setMessagesJson(mapper.writeValueAsString(messages));
        } catch (Exception ex) {
            log.warn("Sérialisation conversation: {}", ex.getMessage());
            throw new BusinessException("Impossible d’enregistrer la conversation", 500);
        }
    }

    private AssistantConversation owned(Long id) {
        return repository.findByIdAndAgent_IdAndArchiveFalse(id, JwtRoles.agentId())
                .orElseThrow(() -> new BusinessException("Conversation introuvable", 404));
    }

    private ConversationDetail toDetail(AssistantConversation conv) {
        return new ConversationDetail(
                conv.getId(),
                conv.getTitle(),
                conv.getCreatedAt(),
                conv.getLastActivityAt(),
                readMessages(conv)
        );
    }

    private List<StoredMessage> readMessages(AssistantConversation conv) {
        try {
            String raw = conv.getMessagesJson();
            if (raw == null || raw.isBlank()) {
                return new ArrayList<>();
            }
            List<StoredMessage> parsed = mapper.readValue(raw, MESSAGES);
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            log.warn("Lecture messages conversation {}: {}", conv.getId(), ex.getMessage());
            return new ArrayList<>();
        }
    }

    static String titleFrom(String message) {
        String compact = message == null ? "" : message.replaceAll("\\s+", " ").strip();
        compact = compact.replaceFirst("(?i)^(hey|hello|salut|bonjour)(?:[,!.\\s]+|$)", "").strip();
        if (compact.isBlank()) {
            return "Conversation";
        }
        if (Character.isLetter(compact.charAt(0))) {
            compact = Character.toUpperCase(compact.charAt(0)) + compact.substring(1);
        }
        if (compact.length() <= TITLE_MAX) {
            return compact;
        }
        int cut = compact.lastIndexOf(' ', TITLE_MAX);
        if (cut < 16) {
            cut = TITLE_MAX;
        }
        return compact.substring(0, cut).strip();
    }
}
