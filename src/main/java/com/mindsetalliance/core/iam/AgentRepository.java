package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long>, JpaSpecificationExecutor<Agent> {
    Optional<Agent> findByEmailProIgnoreCase(String emailPro);
}
