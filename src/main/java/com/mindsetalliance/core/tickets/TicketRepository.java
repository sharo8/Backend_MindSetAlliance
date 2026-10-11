package com.mindsetalliance.core.tickets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findByReference(String reference);
    Optional<Ticket> findBySourceReference(String sourceReference);

    @Query("""
            select t.statut, count(t) from Ticket t
            where t.archive = false
              and (:code is null or lower(t.project.code) = lower(:code))
            group by t.statut
            """)
    List<Object[]> countGroupedByStatut(@Param("code") String code);

    @Query("""
            select t.priorite, count(t) from Ticket t
            where t.archive = false
              and t.statut in :openStatuts
            group by t.priorite
            """)
    List<Object[]> countOpenGroupedByPriorite(@Param("openStatuts") Collection<String> openStatuts);

    @Query("""
            select count(t) from Ticket t
            where t.archive = false
              and lower(t.project.code) = lower(:code)
            """)
    long countActiveByProjectCode(@Param("code") String code);

    @Query("""
            select count(t) from Ticket t
            where t.archive = false
              and t.statut in :openStatuts
            """)
    long countOpen(@Param("openStatuts") Collection<String> openStatuts);
}
