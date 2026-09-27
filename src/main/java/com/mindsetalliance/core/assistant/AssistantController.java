package com.mindsetalliance.core.assistant;

import com.mindsetalliance.core.assistant.AssistantDtos.ChatRequest;
import com.mindsetalliance.core.assistant.AssistantDtos.ChatResponse;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationDetail;
import com.mindsetalliance.core.assistant.AssistantDtos.ConversationPage;
import com.mindsetalliance.core.assistant.AssistantDtos.StatusResponse;
import com.mindsetalliance.core.common.security.RequireRoles;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assistant")
public class AssistantController {

    private final AssistantChatService chatService;
    private final AssistantConversationService conversationService;
    private final AssistantProperties properties;

    public AssistantController(AssistantChatService chatService,
                               AssistantConversationService conversationService,
                               AssistantProperties properties) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.properties = properties;
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/status")
    public StatusResponse status() {
        return StatusResponse.of(properties.configured());
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/conversations")
    public ConversationPage conversations(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return conversationService.list(q, offset, limit);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/conversations/{id}")
    public ConversationDetail conversation(@PathVariable Long id) {
        return conversationService.getOwn(id);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) {
        conversationService.archiveOwn(id);
    }
}
