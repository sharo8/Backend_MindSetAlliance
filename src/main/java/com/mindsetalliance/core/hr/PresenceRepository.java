package com.mindsetalliance.core.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PresenceRepository extends JpaRepository<Presence, Long> {
    List<Presence> findByAgentAndJourBetween(com.mindsetalliance.core.iam.Agent agent, LocalDate start, LocalDate end);
}
