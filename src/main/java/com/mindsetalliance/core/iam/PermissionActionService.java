package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PermissionActionService {

    public static final List<String> ACTIONS = List.of("VIEW", "CREATE", "UPDATE", "DELETE");

    private final AgentRepository agentRepository;
    private final PermissionRepository permissionRepository;
    private final ProjectRepository projectRepository;
    private final AgentPermissionActionRepository actionRepository;
    private final AuditService auditService;

    public PermissionActionService(AgentRepository agentRepository, PermissionRepository permissionRepository,
                                   ProjectRepository projectRepository, AgentPermissionActionRepository actionRepository,
                                   AuditService auditService) {
        this.agentRepository = agentRepository;
        this.permissionRepository = permissionRepository;
        this.projectRepository = projectRepository;
        this.actionRepository = actionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForAgent(Long agentId) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (AgentPermissionAction row : actionRepository.findByAgentId(agentId)) {
            if (row.getPermission() == null) {
                continue;
            }
            Long projectId = row.getProject() == null ? null : row.getProject().getId();
            String key = row.getPermission().getCode() + "|" + projectId;
            Map<String, Object> item = grouped.computeIfAbsent(key, ignored -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("permission", row.getPermission().getCode());
                body.put("projectId", projectId);
                body.put("projectCode", row.getProject() == null ? null : row.getProject().getCode());
                Map<String, Boolean> actions = new LinkedHashMap<>();
                for (String action : ACTIONS) {
                    actions.put(action, Boolean.TRUE);
                }
                body.put("actions", actions);
                return body;
            });
            @SuppressWarnings("unchecked")
            Map<String, Boolean> actions = (Map<String, Boolean>) item.get("actions");
            actions.put(row.getAction().toUpperCase(Locale.ROOT), row.isAllowed());
        }
        return new ArrayList<>(grouped.values());
    }

    @Transactional
    public Map<String, Object> replace(Long agentId, Long permissionId, Long projectId, Map<String, Boolean> incoming) {
        Long current = JwtRoles.agentId();
        if (current != null && current.equals(agentId)) {
            throw new BusinessException("Vous ne pouvez pas modifier vos propres rôles, permissions ou statut.", 403);
        }
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("Permission introuvable", 404));
        if (!permission.isDecomposable()) {
            throw new BusinessException("Cette permission n’est pas décomposable en actions CRUD");
        }
        Project project = null;
        if (projectId != null) {
            project = projectRepository.findById(projectId).orElseThrow(() -> new BusinessException("Projet introuvable", 404));
        }
        Map<String, Boolean> next = defaultAllTrue();
        if (incoming != null) {
            for (String action : ACTIONS) {
                if (incoming.containsKey(action)) {
                    next.put(action, Boolean.TRUE.equals(incoming.get(action)));
                }
            }
        }
        boolean allAllowed = next.values().stream().allMatch(Boolean.TRUE::equals);
        List<AgentPermissionAction> existing = actionRepository.findForPermission(agentId, permissionId, projectId);
        Map<String, Object> avant = toActionMap(existing);
        if (allAllowed) {
            actionRepository.deleteAll(existing);
        } else {
            Map<String, AgentPermissionAction> byAction = new LinkedHashMap<>();
            for (AgentPermissionAction row : existing) {
                byAction.put(row.getAction().toUpperCase(Locale.ROOT), row);
            }
            List<AgentPermissionAction> save = new ArrayList<>();
            for (String action : ACTIONS) {
                AgentPermissionAction row = byAction.remove(action);
                if (row == null) {
                    row = new AgentPermissionAction();
                    row.setAgent(agent);
                    row.setPermission(permission);
                    row.setProject(project);
                    row.setAction(action);
                }
                row.setAllowed(Boolean.TRUE.equals(next.get(action)));
                row.setUpdatedAt(Instant.now());
                save.add(row);
            }
            if (!byAction.isEmpty()) {
                actionRepository.deleteAll(byAction.values());
            }
            actionRepository.saveAll(save);
        }
        auditService.record(current, "UPDATE_PERMISSION_ACTIONS", "AGENT_PERMISSION_ACTION", agentId,
                avant, Map.of("permission", permission.getCode(), "projectId", projectId == null ? "" : projectId, "actions", next));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("permission", permission.getCode());
        body.put("projectId", projectId);
        body.put("actions", next);
        body.put("message", "Actions CRUD enregistrées.");
        return body;
    }

    public static Map<String, Boolean> defaultAllTrue() {
        Map<String, Boolean> actions = new LinkedHashMap<>();
        for (String action : ACTIONS) {
            actions.put(action, Boolean.TRUE);
        }
        return actions;
    }

    private static Map<String, Object> toActionMap(List<AgentPermissionAction> rows) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (AgentPermissionAction row : rows) {
            map.put(row.getAction(), row.isAllowed());
        }
        return map;
    }

    public static boolean isDecomposableCode(String code) {
        if (code == null || !code.startsWith("MANAGE_")) {
            return false;
        }
        return !Set.of("MANAGE_OWN_PROFILE", "MANAGE_ACCESS", "MANAGE_PERMISSIONS").contains(code);
    }
}
