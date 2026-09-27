package com.mindsetalliance.core.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findTop200ByActionInOrderByCreatedAtDesc(Collection<String> actions);

    Optional<AuditLog> findFirstByObjetTypeAndObjetIdAndActionInOrderByCreatedAtDesc(
            String objetType, Long objetId, Collection<String> actions);

    @Query("select a from AuditLog a left join fetch a.agent where a.action = :action order by a.createdAt desc")
    List<AuditLog> findRecentByAction(@Param("action") String action, Pageable pageable);

    @Query("select a from AuditLog a where a.action = :action and a.createdAt >= :from")
    List<AuditLog> findByActionSince(@Param("action") String action, @Param("from") Instant from);

    @Query("select a from AuditLog a left join fetch a.agent where a.createdAt >= :from order by a.createdAt desc")
    List<AuditLog> findCreatedSince(@Param("from") Instant from);
}
