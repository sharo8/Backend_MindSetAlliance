package com.mindsetalliance.core.tickets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketStatutHistoriqueRepository extends JpaRepository<TicketStatutHistorique, Long> {
    List<TicketStatutHistorique> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
