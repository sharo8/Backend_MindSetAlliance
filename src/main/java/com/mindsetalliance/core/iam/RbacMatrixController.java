package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.security.RequirePermissions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final RbacMatrixService rbacMatrixService;

    public RbacMatrixController(RoleRepository roleRepository, PermissionRepository permissionRepository,
                                AuditService auditService, RbacMatrixService rbacMatrixService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
        this.rbacMatrixService = rbacMatrixService;
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

    @RequirePermissions({"MANAGE_PERMISSIONS", "MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/api/roles/{id}/permissions")
    @Transactional(readOnly = true)
    public Map<String, Object> rolePermissions(@PathVariable Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BusinessException("Rôle introuvable", 404));
        Map<String, Object> body = toRole(role);
        body.put("permissions", permissionRepository.findCodesByRoleId(id));
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

    @RequirePermissions({"MANAGE_PERMISSIONS"})
    @PostMapping("/api/roles/permissions-matrix/preview")
    public Map<String, Object> previewMatrix(@RequestBody MatrixUpdate body) {
        return rbacMatrixService.preview(body.roles());
    }

    @RequirePermissions({"MANAGE_PERMISSIONS"})
    @PutMapping("/api/roles/permissions-matrix")
    public Map<String, Object> saveMatrix(@RequestBody MatrixUpdate body) {
        return rbacMatrixService.save(body.roles());
    }

    private Map<String, Object> toRole(Role role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", role.getId());
        row.put("nom", role.getNom());
        row.put("libelle", rbacMatrixService.roleLibelle(role.getNom()));
        row.put("agentsCount", rbacMatrixService.agentsCount(role.getId()));
        return row;
    }

    private Map<String, Object> toPermission(Permission permission) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", permission.getId());
        row.put("code", permission.getCode());
        row.put("module", permission.getModule() == null ? "Général" : permission.getModule());
        row.put("libelle", permission.getLibelle() == null || permission.getLibelle().isBlank()
                || permission.getLibelle().equals(permission.getCode())
                ? defaultPermissionLibelle(permission.getCode())
                : permission.getLibelle());
        row.put("decomposable", permission.isDecomposable());
        return row;
    }

    private static String defaultPermissionLibelle(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "VIEW_VITRINE" -> "Voir la vitrine consolidée";
            default -> code;
        };
    }

    public record PermissionUpdate(List<String> permissions) {}
    public record MatrixUpdate(List<RbacMatrixService.RolePermissionPatch> roles) {}
}
