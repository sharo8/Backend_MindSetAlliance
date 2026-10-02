package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.common.security.RequirePermissions;
import com.mindsetalliance.core.common.security.RequireRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AccessAdminController {

    private final AccessAdminService service;
    private final AgentDirectoryService directoryService;
    private final DepartementAdminService departementAdminService;
    private final PermissionActionService permissionActionService;

    public AccessAdminController(AccessAdminService service, AgentDirectoryService directoryService,
                                 DepartementAdminService departementAdminService,
                                 PermissionActionService permissionActionService) {
        this.service = service;
        this.directoryService = directoryService;
        this.departementAdminService = departementAdminService;
        this.permissionActionService = permissionActionService;
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents")
    public Page<Map<String, Object>> agents(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) Long departement,
                                            @RequestParam(required = false) Long departementId,
                                            @RequestParam(required = false) String statut,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(required = false) String scope,
                                            Pageable pageable) {
        String query = search != null && !search.isBlank() ? search : q;
        Long dep = departement != null ? departement : departementId;
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return directoryService.list(query, dep, statut, role, scope, pageable);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/access-stats")
    public Map<String, Object> accessStats() {
        return service.accessStats();
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents/{id}/access-history")
    public List<Map<String, Object>> accessHistory(@PathVariable Long id) {
        return service.accessHistory(id);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PutMapping("/agents/{id}/access")
    public Map<String, Object> syncAccess(@PathVariable Long id, @RequestBody AccessSyncBody body) {
        return service.syncAccess(id, body.companyRoleId(), body.projects() == null ? List.of() : body.projects());
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents/{id}")
    public Map<String, Object> agent(@PathVariable Long id) {
        return directoryService.get(id);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents/{id}/permissions-effectives")
    public List<String> permissionsEffectives(@PathVariable Long id, @RequestParam(required = false) String projectCode) {
        return directoryService.permissionsEffectives(id, projectCode);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "CREATE")
    @PostMapping("/agents")
    public Map<String, Object> createAgent(@RequestBody @Valid CreateAgentBody body) {
        return directoryService.create(new AgentDirectoryService.CreateRequest(
                body.nom(), body.prenom(), body.emailPro(), body.telephone(), body.departementId(), body.roles(), body.photo()));
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "UPDATE")
    @PatchMapping("/agents/{id}")
    public Map<String, Object> patchAgent(@PathVariable Long id, @RequestBody AgentDirectoryService.UpdateRequest body) {
        return directoryService.update(id, body);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "DELETE")
    @PostMapping("/agents/{id}/desactiver")
    public Map<String, Object> desactiver(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return directoryService.desactiver(id, body == null ? null : body.get("motif"));
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "DELETE")
    @PostMapping("/agents/{id}/supprimer")
    public Map<String, Object> supprimer(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return directoryService.supprimer(id, body == null ? null : body.get("motif"));
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "UPDATE")
    @PostMapping("/agents/{id}/reactiver")
    public Map<String, Object> reactiver(@PathVariable Long id) {
        return directoryService.reactiver(id);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PutMapping("/agents/{id}/permission-actions")
    public Map<String, Object> replaceActions(@PathVariable Long id, @RequestBody PermissionActionsBody body) {
        return permissionActionService.replace(id, body.permissionId(), body.projectId(), body.actions());
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PostMapping("/agents/{id}/permissions/override")
    public Map<String, Object> addOverride(@PathVariable Long id, @RequestBody OverrideBody body) {
        return directoryService.addOverride(id, body.permissionId(), body.projectId(), body.type(), body.motif());
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @DeleteMapping("/agents/{id}/permissions/override/{overrideId}")
    public Map<String, Object> removeOverride(@PathVariable Long id, @PathVariable Long overrideId) {
        return directoryService.removeOverride(id, overrideId);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents/{id}/roles")
    public List<Map<String, Object>> agentRoles(@PathVariable Long id) {
        return service.rolesOf(id);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PostMapping("/agents/{id}/roles")
    public Map<String, Object> assign(@PathVariable Long id, @RequestBody @Valid AssignBody body) {
        return service.assign(id, body.roleId(), body.projectId());
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @DeleteMapping("/agents/{id}/roles/{agentRoleId}")
    public Map<String, Object> revoke(@PathVariable Long id, @PathVariable Long agentRoleId) {
        return service.revoke(id, agentRoleId);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/departements")
    public List<Map<String, Object>> departements() {
        return departementAdminService.list();
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping("/departements")
    public Map<String, Object> createDepartement(@RequestBody DepartementBody body) {
        return departementAdminService.create(body.nom(), body.description(), body.roles());
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PutMapping("/departements/{id}")
    public Map<String, Object> updateDepartement(@PathVariable Long id, @RequestBody DepartementBody body) {
        return departementAdminService.update(id, body.nom(), body.description(), body.roles(), body.statut());
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping("/departements/{id}/desactiver")
    public Map<String, Object> deactivateDepartement(@PathVariable Long id) {
        return departementAdminService.desactiver(id);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return service.catalogRoles();
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/projects")
    public List<Map<String, Object>> projects() {
        return service.catalogProjects();
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/societes")
    public List<Map<String, Object>> societes() {
        return service.listSocietes();
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping("/societes")
    public Map<String, Object> createSociete(@RequestBody SocieteBody body) {
        return service.createSociete(body.nom(), body.code(), body.villeReference());
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PutMapping("/societes/{id}")
    public Map<String, Object> updateSociete(@PathVariable Long id, @RequestBody SocieteBody body) {
        return service.updateSociete(id, body.nom(), body.villeReference(), body.statut());
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping("/societes/{id}/desactiver")
    public Map<String, Object> deactivateSociete(@PathVariable Long id) {
        return service.deactivateSociete(id);
    }

    public record AssignBody(@NotNull Long roleId, Long projectId) {}
    public record AccessSyncBody(Long companyRoleId, List<AccessAdminService.ProjectAccess> projects) {}
    public record SocieteBody(String nom, String code, String villeReference, String statut) {}
    public record CreateAgentBody(@NotBlank String nom, @NotBlank String prenom, @Email @NotBlank String emailPro,
                                  String telephone, @NotNull Long departementId,
                                  List<AgentDirectoryService.RoleAssign> roles, String photo) {}
    public record DepartementBody(String nom, String description, List<String> roles, String statut) {}
    public record OverrideBody(@NotNull Long permissionId, Long projectId, @NotBlank String type, String motif) {}
    public record PermissionActionsBody(@NotNull Long permissionId, Long projectId, Map<String, Boolean> actions) {}
}
