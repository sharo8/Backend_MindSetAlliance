package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentRoleRepository extends JpaRepository<AgentRole, Long> {
    List<AgentRole> findByAgentId(Long agentId);
    void deleteByAgentId(Long agentId);

    @Query("""
            select count(ar) from AgentRole ar
            where ar.agent.id = :agentId and ar.role.id = :roleId
              and ((:projectId is null and ar.project is null) or ar.project.id = :projectId)
            """)
    long countAssignment(@Param("agentId") Long agentId, @Param("roleId") Long roleId, @Param("projectId") Long projectId);

    @Query("""
            select count(distinct ar.agent.id) from AgentRole ar
            where ar.project.id = :projectId or ar.project is null
            """)
    long countAgentsWithAccess(@Param("projectId") Long projectId);

    @Query("""
            select count(distinct ar.agent.id) from AgentRole ar
            where ar.role.id = :roleId
            """)
    long countDistinctAgentsByRoleId(@Param("roleId") Long roleId);

    @Query("""
            select ar from AgentRole ar
            join fetch ar.agent
            join fetch ar.role
            where ar.role.id in :roleIds
            """)
    List<AgentRole> findWithAgentByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Query("""
            select ar from AgentRole ar
            join fetch ar.agent
            join fetch ar.role
            left join fetch ar.project
            """)
    List<AgentRole> findAllWithGraph();
}
