package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.common.security.RequirePermissions;
import com.mindsetalliance.core.common.security.RequireRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public AccessAdminController(AccessAdminService service, AgentDirectoryService directoryService) {
        this.service = service;
        this.directoryService = directoryService;
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents")
    public Page<Map<String, Object>> agents(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) Long departement,
                                            @RequestParam(required = false) Long departementId,
                                            @RequestParam(required = false) String statut,
                                            Pageable pageable) {
        String query = search != null && !search.isBlank() ? search : q;
        Long dep = departement != null ? departement : departementId;
        return directoryService.list(query, dep, statut, pageable);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @GetMapping("/agents/{id}")
    public Map<String, Object> agent(@PathVariable Long id) {
        return directoryService.get(id);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PostMapping("/agents")
    public Map<String, Object> createAgent(@RequestBody @Valid CreateAgentBody body) {
        return directoryService.create(new AgentDirectoryService.CreateRequest(
                body.nom(), body.prenom(), body.emailPro(), body.telephone(), body.departementId(), body.roles()));
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PatchMapping("/agents/{id}")
    public Map<String, Object> patchAgent(@PathVariable Long id, @RequestBody AgentDirectoryService.UpdateRequest body) {
        return directoryService.update(id, body);
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PostMapping("/agents/{id}/desactiver")
    public Map<String, Object> desactiver(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return directoryService.desactiver(id, body == null ? null : body.get("motif"));
    }

    @RequirePermissions({"MANAGE_USERS", "MANAGE_ACCESS"})
    @PostMapping("/agents/{id}/reactiver")
    public Map<String, Object> reactiver(@PathVariable Long id) {
        return directoryService.reactiver(id);
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
        return directoryService.listDepartements();
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
    public record SocieteBody(String nom, String code, String villeReference, String statut) {}
    public record CreateAgentBody(@NotBlank String nom, @NotBlank String prenom, @Email @NotBlank String emailPro,
                                  String telephone, Long departementId, List<AgentDirectoryService.RoleAssign> roles) {}
    public record OverrideBody(@NotNull Long permissionId, Long projectId, @NotBlank String type, @NotBlank String motif) {}
}
