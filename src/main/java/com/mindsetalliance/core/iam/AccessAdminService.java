package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccessAdminService {

    private final AgentRepository agentRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final AuditService auditService;

    public AccessAdminService(AgentRepository agentRepository, RoleRepository roleRepository,
                              ProjectRepository projectRepository, AgentRoleRepository agentRoleRepository,
                              AuditService auditService) {
        this.agentRepository = agentRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listAgents(String q, Pageable pageable) {
        Specification<Agent> spec = (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("nom")), like));
            predicates.add(cb.like(cb.lower(root.get("prenom")), like));
            predicates.add(cb.like(cb.lower(root.get("emailPro")), like));
            Join<Agent, Departement> dep = root.join("departement", JoinType.LEFT);
            predicates.add(cb.like(cb.lower(dep.get("nom")), like));
            return cb.or(predicates.toArray(Predicate[]::new));
        };
        return agentRepository.findAll(spec, pageable).map(this::toAgent);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rolesOf(Long agentId) {
        agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        return agentRoleRepository.findByAgentId(agentId).stream().map(this::toAssignment).toList();
    }

    @Transactional
    public Map<String, Object> assign(Long agentId, Long roleId, Long projectId) {
        if (JwtRoles.agentId().equals(agentId)) {
            throw new BusinessException("Vous ne pouvez pas modifier vos propres rôles, permissions ou statut.", 403);
        }
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new BusinessException("Rôle introuvable", 404));
        Project project = null;
        if (projectId != null) {
            project = projectRepository.findById(projectId).orElseThrow(() -> new BusinessException("Projet introuvable", 404));
        }
        if (agentRoleRepository.countAssignment(agentId, roleId, projectId) > 0) {
            throw new BusinessException("Ce rôle est déjà attribué pour ce périmètre", 409);
        }
        AgentRole assignment = new AgentRole();
        assignment.setAgent(agent);
        assignment.setRole(role);
        assignment.setProject(project);
        assignment.setDateDebut(LocalDate.now());
        agentRoleRepository.save(assignment);
        String projectCode = project == null ? "ENTREPRISE" : project.getCode();
        auditService.record(JwtRoles.agentId(), "ASSIGN_ROLE", "AGENT_ROLE", assignment.getId(), null,
                Map.of(
                        "cible", agent.getEmailPro(),
                        "cibleId", agent.getId(),
                        "role", role.getNom(),
                        "project", projectCode
                ));
        Map<String, Object> body = toAssignment(assignment);
        body.put("message", "Rôle attribué.");
        return body;
    }

    @Transactional
    public Map<String, Object> revoke(Long agentId, Long agentRoleId) {
        if (JwtRoles.agentId().equals(agentId)) {
            throw new BusinessException("Vous ne pouvez pas modifier vos propres rôles, permissions ou statut.", 403);
        }
        AgentRole assignment = agentRoleRepository.findById(agentRoleId)
                .orElseThrow(() -> new BusinessException("Attribution introuvable", 404));
        if (assignment.getAgent() == null || !assignment.getAgent().getId().equals(agentId)) {
            throw new BusinessException("Cette attribution n'appartient pas à cet agent", 404);
        }
        String roleNom = assignment.getRole().getNom();
        String projectCode = assignment.getProject() == null ? "ENTREPRISE" : assignment.getProject().getCode();
        String cible = assignment.getAgent().getEmailPro();
        agentRoleRepository.delete(assignment);
        auditService.record(JwtRoles.agentId(), "REVOKE_ROLE", "AGENT_ROLE", agentRoleId,
                Map.of("role", roleNom, "project", projectCode, "cible", cible, "cibleId", agentId),
                Map.of("revoked", true));
        return Map.of("message", "Rôle révoqué.", "id", agentRoleId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalogRoles() {
        return roleRepository.findAll().stream().map(role -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", role.getId());
            row.put("nom", role.getNom());
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalogProjects() {
        return projectRepository.findAll().stream().map(this::toSociete).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSocietes() {
        return projectRepository.findAll().stream().map(this::toSociete).toList();
    }

    @Transactional
    public Map<String, Object> createSociete(String nom, String code, String villeReference) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (nom == null || nom.isBlank()) {
            throw new BusinessException("Le nom de la société est obligatoire");
        }
        if (normalized.isBlank() || normalized.length() > 10) {
            throw new BusinessException("Le code projet est obligatoire (10 caractères max.)");
        }
        if (projectRepository.findByCode(normalized).isPresent()) {
            throw new BusinessException("Ce code projet existe déjà");
        }
        Project project = new Project();
        project.setNom(nom.trim());
        project.setCode(normalized);
        project.setVilleReference(villeReference == null || villeReference.isBlank() ? null : villeReference.trim());
        project.setStatut("ACTIF");
        project.setCreatedAt(Instant.now());
        projectRepository.save(project);
        auditService.record(JwtRoles.agentId(), "CREATE", "SOCIETE", project.getId(), null,
                Map.of("code", project.getCode(), "nom", project.getNom()));
        return toSociete(project);
    }

    @Transactional
    public Map<String, Object> updateSociete(Long id, String nom, String villeReference, String statut) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new BusinessException("Société introuvable", 404));
        Map<String, Object> avant = Map.of("nom", String.valueOf(project.getNom()), "statut", String.valueOf(project.getStatut()));
        if (nom != null && !nom.isBlank()) {
            project.setNom(nom.trim());
        }
        if (villeReference != null) {
            project.setVilleReference(villeReference.isBlank() ? null : villeReference.trim());
        }
        if (statut != null && List.of("ACTIF", "EN_PREPARATION", "INACTIF").contains(statut.toUpperCase())) {
            project.setStatut(statut.toUpperCase());
        }
        auditService.record(JwtRoles.agentId(), "UPDATE", "SOCIETE", id, avant,
                Map.of("nom", project.getNom(), "statut", project.getStatut()));
        return toSociete(project);
    }

    @Transactional
    public Map<String, Object> deactivateSociete(Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new BusinessException("Société introuvable", 404));
        String avant = project.getStatut();
        project.setStatut("INACTIF");
        auditService.record(JwtRoles.agentId(), "ARCHIVE", "SOCIETE", id, Map.of("statut", avant), Map.of("statut", "INACTIF"));
        return toSociete(project);
    }

    private Map<String, Object> toSociete(Project project) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", project.getId());
        row.put("code", project.getCode());
        row.put("nom", project.getNom());
        row.put("statut", project.getStatut());
        row.put("villeReference", project.getVilleReference());
        row.put("createdAt", project.getCreatedAt());
        row.put("agentsCount", agentRoleRepository.countAgentsWithAccess(project.getId()));
        return row;
    }

    private Map<String, Object> toAgent(Agent agent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", agent.getId());
        row.put("nom", agent.getNom());
        row.put("prenom", agent.getPrenom());
        row.put("emailPro", agent.getEmailPro());
        row.put("statut", agent.getStatut());
        if (agent.getDepartement() != null) {
            row.put("departement", Map.of(
                    "id", agent.getDepartement().getId(),
                    "code", agent.getDepartement().getCode(),
                    "nom", agent.getDepartement().getNom()
            ));
        } else {
            row.put("departement", null);
        }
        row.put("roles", agentRoleRepository.findByAgentId(agent.getId()).stream().map(this::toAssignment).toList());
        return row;
    }

    private Map<String, Object> toAssignment(AgentRole assignment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", assignment.getId());
        row.put("roleId", assignment.getRole().getId());
        row.put("role", assignment.getRole().getNom());
        row.put("projectId", assignment.getProject() == null ? null : assignment.getProject().getId());
        row.put("projectCode", assignment.getProject() == null ? null : assignment.getProject().getCode());
        row.put("projectNom", assignment.getProject() == null ? null : assignment.getProject().getNom());
        row.put("dateDebut", assignment.getDateDebut());
        row.put("dateFin", assignment.getDateFin());
        return row;
    }
}
