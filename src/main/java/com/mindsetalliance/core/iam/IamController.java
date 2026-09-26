package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.RequireRoles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/iam")
@Validated
public class IamController {

    private final IamService iamService;

    public IamController(IamService iamService) {
        this.iamService = iamService;
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION", "RH"})
    @GetMapping("/agents")
    public List<AgentDto> agents() {
        return iamService.listAgents();
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION", "RH"})
    @PostMapping("/agents")
    public AgentDto createAgent(@RequestBody @jakarta.validation.Valid AgentCreateRequest request) {
        return iamService.createAgent(request);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping("/agents/{id}/roles")
    public AgentRoleDto assignRole(@PathVariable Long id, @RequestBody AgentRoleRequest request) {
        return iamService.assignRole(id, request);
    }

    @RequireRoles({"ADMIN_SYSTEME"})
    @PutMapping("/agents/{id}/statut")
    public AgentDto changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return iamService.changeStatus(id, body.get("statut"));
    }

    @GetMapping("/projects")
    public List<Project> projects() {
        return iamService.listProjects();
    }

    @GetMapping("/roles")
    public List<Role> roles() {
        return iamService.listRoles();
    }

    public record AgentCreateRequest(@Email @NotBlank String emailPro, @NotBlank String password,
                                     @NotBlank String nom, @NotBlank String prenom, Long departementId) {}
    public record AgentRoleRequest(@NotBlank String role, String projectCode, LocalDate dateDebut, LocalDate dateFin) {}
    public record AgentDto(Long id, String emailPro, String nom, String prenom, String statut, boolean twoFactorEnabled,
                           List<AgentRoleDto> roles) {}
    public record AgentRoleDto(Long id, String role, String projectCode, LocalDate dateDebut, LocalDate dateFin) {}
}

@org.springframework.stereotype.Service
class IamService {
    private final AgentRepository agentRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;
    private final DepartementRepository departementRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    IamService(AgentRepository agentRepository, RoleRepository roleRepository, ProjectRepository projectRepository,
               DepartementRepository departementRepository, AgentRoleRepository agentRoleRepository,
               PasswordEncoder passwordEncoder, AuditService auditService) {
        this.agentRepository = agentRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
        this.departementRepository = departementRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<IamController.AgentDto> listAgents() {
        return agentRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public IamController.AgentDto createAgent(IamController.AgentCreateRequest request) {
        if (agentRepository.findByEmailProIgnoreCase(request.emailPro()).isPresent()) {
            throw new BusinessException("Un agent existe déjà avec cet e-mail professionnel");
        }
        Agent agent = new Agent();
        agent.setEmailPro(request.emailPro());
        agent.setPasswordHash(passwordEncoder.encode(request.password()));
        agent.setNom(request.nom());
        agent.setPrenom(request.prenom());
        agent.setStatut("ACTIF");
        if (request.departementId() != null) {
            agent.setDepartement(departementRepository.findById(request.departementId())
                    .orElseThrow(() -> new BusinessException("Département introuvable")));
        }
        agentRepository.save(agent);
        auditService.record(agent.getId(), "CREATE", "AGENT", agent.getId(), null, Map.of("email", agent.getEmailPro()));
        return toDto(agent);
    }

    @Transactional
    public IamController.AgentRoleDto assignRole(Long agentId, IamController.AgentRoleRequest request) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Role role = roleRepository.findByNom(request.role()).orElseThrow(() -> new BusinessException("Rôle introuvable"));
        Project project = null;
        if (request.projectCode() != null && !request.projectCode().isBlank()) {
            project = projectRepository.findByCode(request.projectCode())
                    .orElseThrow(() -> new BusinessException("Projet introuvable"));
        }
        AgentRole assignment = new AgentRole();
        assignment.setAgent(agent);
        assignment.setRole(role);
        assignment.setProject(project);
        assignment.setDateDebut(request.dateDebut() == null ? LocalDate.now() : request.dateDebut());
        assignment.setDateFin(request.dateFin());
        agentRoleRepository.save(assignment);
        auditService.record(agentId, "ASSIGN_ROLE", "AGENT_ROLE", assignment.getId(), null,
                Map.of("role", role.getNom(), "project", project == null ? "ENTREPRISE" : project.getCode()));
        return new IamController.AgentRoleDto(assignment.getId(), role.getNom(),
                project == null ? null : project.getCode(), assignment.getDateDebut(), assignment.getDateFin());
    }

    @Transactional
    public IamController.AgentDto changeStatus(Long id, String statut) {
        if (statut == null || !List.of("ACTIF", "SUSPENDU", "DESACTIVE").contains(statut)) {
            throw new BusinessException("Statut agent invalide");
        }
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        String before = agent.getStatut();
        agent.setStatut(statut);
        auditService.record(id, "CHANGE_STATUS", "AGENT", id, Map.of("statut", before), Map.of("statut", statut));
        return toDto(agent);
    }

    public List<Project> listProjects() {
        Set<String> codes = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
        return projectRepository.findAll().stream()
                .filter(p -> p.getStatut() == null || !List.of("INACTIF", "DESACTIVE").contains(p.getStatut().toUpperCase()))
                .filter(p -> codes.contains("*") || codes.contains(p.getCode()))
                .toList();
    }
    public List<Role> listRoles() { return roleRepository.findAll(); }

    private IamController.AgentDto toDto(Agent agent) {
        List<IamController.AgentRoleDto> roles = agentRoleRepository.findByAgentId(agent.getId()).stream()
                .map(ar -> new IamController.AgentRoleDto(
                        ar.getId(),
                        ar.getRole().getNom(),
                        ar.getProject() == null ? null : ar.getProject().getCode(),
                        ar.getDateDebut(),
                        ar.getDateFin()))
                .toList();
        return new IamController.AgentDto(agent.getId(), agent.getEmailPro(), agent.getNom(), agent.getPrenom(),
                agent.getStatut(), agent.isTwoFactorEnabled(), roles);
    }
}
