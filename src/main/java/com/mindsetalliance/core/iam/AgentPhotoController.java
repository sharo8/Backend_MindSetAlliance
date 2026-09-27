package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AgentPhotoController {

    private final AgentRepository agentRepository;

    public AgentPhotoController(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @GetMapping("/api/agents/{id}/photo")
    public Map<String, Object> photo(@PathVariable Long id) {
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", agent.getId());
        body.put("hasPhoto", PhotoProfilUtil.hasPhoto(agent));
        body.put("photo", PhotoProfilUtil.toDataUrl(agent));
        return body;
    }
}
