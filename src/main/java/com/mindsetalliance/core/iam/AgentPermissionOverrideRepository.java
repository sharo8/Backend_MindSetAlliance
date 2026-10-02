package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentPermissionOverrideRepository extends JpaRepository<AgentPermissionOverride, Long> {
    List<AgentPermissionOverride> findByAgentId(Long agentId);
    long countByAgentId(Long agentId);
    Optional<AgentPermissionOverride> findByIdAndAgentId(Long id, Long agentId);

    @Query("""
            select distinct o from AgentPermissionOverride o
            join fetch o.agent
            join fetch o.permission
            left join fetch o.project
            left join fetch o.accordePar
            """)
    List<AgentPermissionOverride> findAllWithGraph();

    @Query("""
            select o from AgentPermissionOverride o
            where o.agent.id = :agentId and o.permission.id = :permissionId
              and ((:projectId is null and o.project is null) or o.project.id = :projectId)
            """)
    Optional<AgentPermissionOverride> findDuplicate(@Param("agentId") Long agentId,
                                                    @Param("permissionId") Long permissionId,
                                                    @Param("projectId") Long projectId);
}
