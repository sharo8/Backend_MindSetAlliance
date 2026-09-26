package com.mindsetalliance.core.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DossierPersonnelRepository extends JpaRepository<DossierPersonnel, Long> {
    Optional<DossierPersonnel> findByAgentId(Long agentId);
}
