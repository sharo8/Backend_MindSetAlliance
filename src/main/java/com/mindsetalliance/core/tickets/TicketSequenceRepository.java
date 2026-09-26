package com.mindsetalliance.core.tickets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TicketSequenceRepository extends JpaRepository<TicketSequence, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketSequence> findByProjectId(Long projectId);
}
