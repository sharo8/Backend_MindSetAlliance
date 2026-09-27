package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentPermissionActionRepository extends JpaRepository<AgentPermissionAction, Long> {
    List<AgentPermissionAction> findByAgentId(Long agentId);

    @Query("""
            select a from AgentPermissionAction a
            where a.agent.id = :agentId and a.permission.id = :permissionId
              and ((:projectId is null and a.project is null) or a.project.id = :projectId)
            """)
    List<AgentPermissionAction> findForPermission(@Param("agentId") Long agentId,
                                                  @Param("permissionId") Long permissionId,
                                                  @Param("projectId") Long projectId);
}
