package com.mindsetalliance.core.tickets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketReattributionRepository extends JpaRepository<TicketReattribution, Long> {
    List<TicketReattribution> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
