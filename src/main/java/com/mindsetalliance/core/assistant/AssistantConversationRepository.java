package com.mindsetalliance.core.assistant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, Long> {

    Page<AssistantConversation> findByAgent_IdAndArchiveFalseOrderByLastActivityAtDesc(Long agentId, Pageable pageable);

    @Query("""
            select c from AssistantConversation c
            where c.agent.id = :agentId and c.archive = false
              and (lower(c.title) like lower(concat('%', :q, '%'))
                or lower(c.messagesJson) like lower(concat('%', :q, '%')))
            order by c.lastActivityAt desc
            """)
    Page<AssistantConversation> searchOwn(@Param("agentId") Long agentId, @Param("q") String q, Pageable pageable);

    Optional<AssistantConversation> findByIdAndAgent_IdAndArchiveFalse(Long id, Long agentId);
}
