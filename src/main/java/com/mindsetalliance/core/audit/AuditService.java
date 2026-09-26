package com.mindsetalliance.core.audit;

import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final AgentRepository agentRepository;

    public AuditService(AuditLogRepository repository, AgentRepository agentRepository) {
        this.repository = repository;
        this.agentRepository = agentRepository;
    }

    @Transactional
    public void record(Long agentId, String action, String objetType, Long objetId,
                       Map<String, Object> avant, Map<String, Object> apres) {
        AuditLog log = new AuditLog();
        if (agentId != null) {
            Agent agent = agentRepository.findById(agentId).orElse(null);
            log.setAgent(agent);
        }
        log.setAction(action);
        log.setObjetType(objetType);
        log.setObjetId(objetId);
        log.setValeurAvant(avant);
        log.setValeurApres(apres);
        repository.save(log);
    }

    public Page<AuditLog> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
