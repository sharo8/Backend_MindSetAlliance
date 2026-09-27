package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationDetail;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationPage;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationSummary;
import com.mindsetalliance.core.assistant.AssistantDtos.FactCard;
import com.mindsetalliance.core.assistant.AssistantDtos.StoredMessage;
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
import java.util.List;
import java.util.UUID;

@Service
public class AssistantConversationService {

    private static final Logger log = LoggerFactory.getLogger(AssistantConversationService.class);
    private static final int TITLE_MAX = 80;
    private static final TypeReference<List<StoredMessage>> MESSAGES = new TypeReference<>() {
    };

    private final AssistantConversationRepository repository;
    private final AgentRepository agentRepository;
    private final ObjectMapper mapper;

    public AssistantConversationService(AssistantConversationRepository repository,
                                        AgentRepository agentRepository,
                                        ObjectMapper mapper) {
        this.repository = repository;
        this.agentRepository = agentRepository;
        this.mapper = mapper;
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
    }

    @Transactional
    public Long appendTurn(Long conversationId, String userText, String assistantText,
                           List<FactCard> facts, boolean unavailable) {
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
        messages.add(new StoredMessage(UUID.randomUUID().toString(), "user", userText, now, List.of(), false));
        messages.add(new StoredMessage(
                UUID.randomUUID().toString(),
                "assistant",
                assistantText,
                now.plusMillis(5),
                facts == null ? List.of() : facts,
                unavailable
        ));
        try {
            conv.setMessagesJson(mapper.writeValueAsString(messages));
        } catch (Exception ex) {
            log.warn("Sérialisation conversation: {}", ex.getMessage());
            throw new BusinessException("Impossible d’enregistrer la conversation", 500);
        }
        conv.setLastActivityAt(now);
        return repository.save(conv).getId();
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
        if (compact.isBlank()) {
            return "Conversation";
        }
        if (compact.length() <= TITLE_MAX) {
            return compact;
        }
        return compact.substring(0, TITLE_MAX - 1).strip() + "…";
    }
}
