package com.mindsetalliance.core.iam;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class EffectivePermissionService {

    private final AgentRoleRepository agentRoleRepository;
    private final AgentPermissionOverrideRepository overrideRepository;

    public EffectivePermissionService(AgentRoleRepository agentRoleRepository,
                                      AgentPermissionOverrideRepository overrideRepository) {
        this.agentRoleRepository = agentRoleRepository;
        this.overrideRepository = overrideRepository;
    }

    /**
     * Permissions effectives d'un agent pour un projet donné
     *   = (permissions de tous ses rôles sur ce projet)
     *     ∪ (ses GRANT individuels sur ce projet ou globaux)
     *     − (ses DENY individuels sur ce projet ou globaux)
     *
     * Un DENY individuel a TOUJOURS priorité sur un rôle ou un GRANT,
     * même si le rôle donnerait normalement cette permission.
     *
     * @param projectCode code société, ou null / ENTREPRISE pour le périmètre global
     */
    @Transactional(readOnly = true)
    public Set<String> calculerPermissionsEffectives(Long agentId, String projectCode) {
        Set<String> granted = new LinkedHashSet<>();
        for (AgentRole assignment : agentRoleRepository.findByAgentId(agentId)) {
            if (!assignment.isActive() || assignment.getRole() == null) {
                continue;
            }
            if (!roleApplies(assignment, projectCode)) {
                continue;
            }
            for (Permission permission : assignment.getRole().getPermissions()) {
                if (permission.getCode() != null) {
                    granted.add(permission.getCode());
                }
            }
        }
        List<AgentPermissionOverride> overrides = overrideRepository.findByAgentId(agentId);
        for (AgentPermissionOverride override : overrides) {
            if (!"GRANT".equalsIgnoreCase(override.getType())) {
                continue;
            }
            if (!overrideApplies(override, projectCode) || override.getPermission() == null) {
                continue;
            }
            granted.add(override.getPermission().getCode());
        }
        for (AgentPermissionOverride override : overrides) {
            if (!"DENY".equalsIgnoreCase(override.getType())) {
                continue;
            }
            if (!overrideApplies(override, projectCode) || override.getPermission() == null) {
                continue;
            }
            granted.remove(override.getPermission().getCode());
        }
        return granted;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long agentId, String permissionCode, String projectCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        if (isDenied(agentId, permissionCode, projectCode)) {
            return false;
        }
        return calculerPermissionsEffectives(agentId, projectCode).contains(permissionCode);
    }

    @Transactional(readOnly = true)
    public boolean isDenied(Long agentId, String permissionCode, String projectCode) {
        return overrideRepository.findByAgentId(agentId).stream().anyMatch(override ->
                "DENY".equalsIgnoreCase(override.getType())
                        && override.getPermission() != null
                        && permissionCode.equalsIgnoreCase(override.getPermission().getCode())
                        && overrideApplies(override, projectCode));
    }

    private boolean roleApplies(AgentRole assignment, String projectCode) {
        if (projectCode == null || projectCode.isBlank() || "ENTREPRISE".equalsIgnoreCase(projectCode)) {
            return true;
        }
        return assignment.getProject() == null || projectCode.equalsIgnoreCase(assignment.getProject().getCode());
    }

    private boolean overrideApplies(AgentPermissionOverride override, String projectCode) {
        if (override.getProject() == null) {
            return true;
        }
        if (projectCode == null || projectCode.isBlank() || "ENTREPRISE".equalsIgnoreCase(projectCode)) {
            return true;
        }
        return projectCode.equalsIgnoreCase(override.getProject().getCode());
    }
}
