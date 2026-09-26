package com.mindsetalliance.core.notifications;

import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final AgentRepository agentRepository;

    NotificationService(NotificationRepository repository, AgentRepository agentRepository) {
        this.repository = repository;
        this.agentRepository = agentRepository;
    }

    public Notification send(Long agentId, String canal, String titre, String message) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return null;
        }
        Notification n = new Notification();
        n.setAgent(agent);
        n.setCanal(canal);
        n.setTitre(titre);
        n.setMessage(message);
        return repository.save(n);
    }

    public List<Notification> mine(Long agentId) {
        return repository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }
}
