package com.mindsetalliance.core.iam;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EffectivePermissionService {

    private final AgentRoleRepository agentRoleRepository;
    private final AgentPermissionOverrideRepository overrideRepository;
    private final AgentPermissionActionRepository actionRepository;
    private final PermissionRepository permissionRepository;

    public EffectivePermissionService(AgentRoleRepository agentRoleRepository,
                                      AgentPermissionOverrideRepository overrideRepository,
                                      AgentPermissionActionRepository actionRepository,
                                      PermissionRepository permissionRepository) {
        this.agentRoleRepository = agentRoleRepository;
        this.overrideRepository = overrideRepository;
        this.actionRepository = actionRepository;
        this.permissionRepository = permissionRepository;
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
    public boolean hasAction(Long agentId, String permissionCode, String action, String projectCode) {
        if (!hasPermission(agentId, permissionCode, projectCode)) {
            return false;
        }
        Permission permission = permissionRepository.findByCode(permissionCode).orElse(null);
        if (permission == null || !permission.isDecomposable()) {
            return true;
        }
        if (action == null || action.isBlank()) {
            return true;
        }
        Map<String, Boolean> flags = actionsFor(agentId, permission, projectCode);
        return Boolean.TRUE.equals(flags.get(action.toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> actionsFor(Long agentId, Permission permission, String projectCode) {
        Map<String, Boolean> flags = PermissionActionService.defaultAllTrue();
        if (permission == null) {
            return flags;
        }
        boolean customized = false;
        for (AgentPermissionAction row : actionRepository.findByAgentId(agentId)) {
            if (row.getPermission() == null || !permission.getId().equals(row.getPermission().getId())) {
                continue;
            }
            String rowProject = row.getProject() == null ? null : row.getProject().getCode();
            if (!PermissionOverrideScope.applies(rowProject, projectCode)) {
                continue;
            }
            customized = true;
            flags.put(row.getAction().toUpperCase(Locale.ROOT), row.isAllowed());
        }
        if (!customized) {
            return PermissionActionService.defaultAllTrue();
        }
        return flags;
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
        String overrideProject = override.getProject() == null ? null : override.getProject().getCode();
        return PermissionOverrideScope.applies(overrideProject, projectCode);
    }
}
