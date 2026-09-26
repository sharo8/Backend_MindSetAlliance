package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.security.RequirePermissions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class RbacMatrixController {

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "SUPPORT", "Support / Ops",
            "COMMERCIAL", "Commercial",
            "FINANCE", "Finance",
            "RH", "RH",
            "JURIDIQUE", "Juridique",
            "MARKETING", "Marketing",
            "DEV", "Dev / Tech Ops",
            "DIRECTION", "Direction",
            "ADMIN_SYSTEME", "Super Admin",
            "CONSEIL_ADMINISTRATION", "Conseil d'administration"
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    public RbacMatrixController(RoleRepository roleRepository, PermissionRepository permissionRepository,
                                AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @RequirePermissions({"MANAGE_PERMISSIONS", "MANAGE_USERS"})
    @GetMapping("/api/roles")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> roles() {
        return roleRepository.findAll().stream().map(this::toRole).toList();
    }

    @RequirePermissions({"MANAGE_PERMISSIONS", "MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/api/permissions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> permissions() {
        return permissionRepository.findAll().stream().map(this::toPermission).toList();
    }

    @RequirePermissions({"MANAGE_PERMISSIONS"})
    @GetMapping("/api/roles/{id}/permissions")
    @Transactional(readOnly = true)
    public Map<String, Object> rolePermissions(@PathVariable Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BusinessException("Rôle introuvable", 404));
        Map<String, Object> body = toRole(role);
        body.put("permissions", role.getPermissions().stream().map(Permission::getCode).sorted().toList());
        return body;
    }

    @RequirePermissions({"MANAGE_PERMISSIONS"})
    @PutMapping("/api/roles/{id}/permissions")
    @Transactional
    public Map<String, Object> replacePermissions(@PathVariable Long id, @RequestBody PermissionUpdate body) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BusinessException("Rôle introuvable", 404));
        Set<Permission> next = new HashSet<>();
        for (String code : body.permissions() == null ? List.<String>of() : body.permissions()) {
            next.add(permissionRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException("Permission introuvable : " + code)));
        }
        role.getPermissions().clear();
        role.getPermissions().addAll(next);
        auditService.record(JwtRoles.agentId(), "UPDATE_ROLE_PERMISSIONS", "ROLE", id, null,
                Map.of("count", next.size()));
        Map<String, Object> result = toRole(role);
        result.put("permissions", next.stream().map(Permission::getCode).sorted().toList());
        result.put("message", "Matrice de permissions enregistrée.");
        return result;
    }

    private Map<String, Object> toRole(Role role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", role.getId());
        row.put("nom", role.getNom());
        row.put("libelle", ROLE_LABELS.getOrDefault(role.getNom(), role.getNom()));
        return row;
    }

    private Map<String, Object> toPermission(Permission permission) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", permission.getId());
        row.put("code", permission.getCode());
        row.put("module", permission.getModule() == null ? "Général" : permission.getModule());
        row.put("libelle", permission.getLibelle() == null ? permission.getCode() : permission.getLibelle());
        return row;
    }

    public record PermissionUpdate(List<String> permissions) {}
}
