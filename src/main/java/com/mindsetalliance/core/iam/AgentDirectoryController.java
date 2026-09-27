package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.common.security.RequirePermissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@Validated
public class AgentDirectoryController {

    private final AgentDirectoryService service;

    public AgentDirectoryController(AgentDirectoryService service) {
        this.service = service;
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "VIEW")
    @GetMapping
    public Page<Map<String, Object>> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long departementId,
                                          @RequestParam(required = false) String statut,
                                          Pageable pageable) {
        return service.list(q, departementId, statut, pageable);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "CREATE")
    @PostMapping
    public Map<String, Object> create(@RequestBody @Valid CreateBody body) {
        return service.create(new AgentDirectoryService.CreateRequest(
                body.nom(), body.prenom(), body.emailPro(), body.telephone(), body.departementId(), body.roles(), body.photo()));
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "VIEW")
    @GetMapping("/{id:\\d+}")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.get(id);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "UPDATE")
    @PatchMapping("/{id:\\d+}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody AgentDirectoryService.UpdateRequest body) {
        return service.update(id, body);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "UPDATE")
    @PatchMapping("/{id:\\d+}/status")
    public Map<String, Object> status(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String statut = body == null ? null : body.get("statut");
        if (statut != null && List.of("INACTIF", "DESACTIVE", "SUSPENDU").contains(statut.trim().toUpperCase())) {
            return service.desactiver(id, body.get("motif"));
        }
        return service.changeStatus(id, statut);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "DELETE")
    @PostMapping("/{id:\\d+}/desactiver")
    public Map<String, Object> desactiver(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.desactiver(id, body == null ? null : body.get("motif"));
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "DELETE")
    @PostMapping("/{id:\\d+}/supprimer")
    public Map<String, Object> supprimer(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.supprimer(id, body == null ? null : body.get("motif"));
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "UPDATE")
    @PostMapping("/{id:\\d+}/reactiver")
    public Map<String, Object> reactiver(@PathVariable Long id) {
        return service.reactiver(id);
    }

    @RequirePermissions(value = {"MANAGE_USERS"}, action = "VIEW")
    @GetMapping("/departements")
    public List<Map<String, Object>> departements() {
        return service.listDepartements();
    }

    public record CreateBody(@NotBlank String nom, @NotBlank String prenom, @Email @NotBlank String emailPro,
                             String telephone, @NotNull Long departementId, List<AgentDirectoryService.RoleAssign> roles,
                             String photo) {}
}
