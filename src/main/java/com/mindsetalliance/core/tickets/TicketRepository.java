package com.mindsetalliance.core.tickets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findByReference(String reference);
    Optional<Ticket> findBySourceReference(String sourceReference);
}
