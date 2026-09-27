package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditLog;
import com.mindsetalliance.core.audit.AuditLogRepository;
import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.notifications.AgentChangeNotifier;
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
    private final AuditLogRepository auditLogRepository;
    private final AgentChangeNotifier agentChangeNotifier;

    public AccessAdminService(AgentRepository agentRepository, RoleRepository roleRepository,
                              ProjectRepository projectRepository, AgentRoleRepository agentRoleRepository,
                              AuditService auditService, AuditLogRepository auditLogRepository,
                              AgentChangeNotifier agentChangeNotifier) {
        this.agentRepository = agentRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.agentChangeNotifier = agentChangeNotifier;
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
        String before = agentChangeNotifier.snapshot(agentId);
        Map<String, Object> body = assignQuiet(agentId, roleId, projectId);
        agentChangeNotifier.notifyIfChanged(agentId, before);
        return body;
    }

    private Map<String, Object> assignQuiet(Long agentId, Long roleId, Long projectId) {
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
        AgentRole assignment = agentRoleRepository.findById(agentRoleId)
                .orElseThrow(() -> new BusinessException("Attribution introuvable", 404));
        Long cibleId = assignment.getAgent() == null ? agentId : assignment.getAgent().getId();
        String before = agentChangeNotifier.snapshot(cibleId);
        Map<String, Object> body = revokeQuiet(agentId, agentRoleId);
        agentChangeNotifier.notifyIfChanged(cibleId, before);
        return body;
    }

    private Map<String, Object> revokeQuiet(Long agentId, Long agentRoleId) {
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
        row.put("hasPhoto", agent.isHasPhoto());
        row.put("createdAt", agent.getCreatedAt());
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

    @Transactional(readOnly = true)
    public Map<String, Object> accessStats() {
        List<Agent> all = agentRepository.findAll();
        long actifs = all.stream().filter(a -> "ACTIF".equals(a.getStatut())).count();
        long entreprise = 0;
        long projetSeul = 0;
        for (Agent agent : all) {
            List<AgentRole> roles = agentRoleRepository.findByAgentId(agent.getId());
            boolean wide = roles.stream().anyMatch(r -> r.getProject() == null);
            long projects = roles.stream()
                    .filter(r -> r.getProject() != null)
                    .map(r -> r.getProject().getId())
                    .distinct()
                    .count();
            if (wide) {
                entreprise++;
            } else if (projects == 1) {
                projetSeul++;
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", all.size());
        body.put("actifs", actifs);
        body.put("entreprise", entreprise);
        body.put("projetSeul", projetSeul);
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> accessHistory(Long agentId) {
        agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        return auditLogRepository
                .findTop200ByActionInOrderByCreatedAtDesc(List.of("ASSIGN_ROLE", "REVOKE_ROLE", "CREATE"))
                .stream()
                .filter(log -> matchesAgent(log, agentId))
                .limit(40)
                .map(this::toHistory)
                .toList();
    }

    @Transactional
    public Map<String, Object> syncAccess(Long agentId, Long companyRoleId, List<ProjectAccess> projects) {
        String before = agentChangeNotifier.snapshot(agentId);
        replaceScope(agentId, null, companyRoleId);
        for (ProjectAccess row : projects) {
            if (row == null || row.projectId() == null) {
                continue;
            }
            replaceScope(agentId, row.projectId(), row.roleId());
        }
        agentChangeNotifier.notifyIfChanged(agentId, before);
        return Map.of("message", "Accès mis à jour.", "roles", rolesOf(agentId));
    }

    private void replaceScope(Long agentId, Long projectId, Long roleId) {
        List<AgentRole> current = agentRoleRepository.findByAgentId(agentId).stream()
                .filter(ar -> projectId == null
                        ? ar.getProject() == null
                        : ar.getProject() != null && ar.getProject().getId().equals(projectId))
                .toList();
        if (roleId == null) {
            for (AgentRole assignment : current) {
                revokeQuiet(agentId, assignment.getId());
            }
            return;
        }
        boolean already = current.stream().anyMatch(ar -> ar.getRole().getId().equals(roleId));
        for (AgentRole assignment : current) {
            if (!assignment.getRole().getId().equals(roleId)) {
                revokeQuiet(agentId, assignment.getId());
            }
        }
        if (!already) {
            assignQuiet(agentId, roleId, projectId);
        }
    }

    private boolean matchesAgent(AuditLog log, Long agentId) {
        if ("CREATE".equals(log.getAction()) && "AGENT".equals(log.getObjetType()) && agentId.equals(log.getObjetId())) {
            return true;
        }
        Object id = log.getValeurApres() != null ? log.getValeurApres().get("cibleId") : null;
        if (id == null && log.getValeurAvant() != null) {
            id = log.getValeurAvant().get("cibleId");
        }
        if (id instanceof Number number) {
            return agentId.equals(number.longValue());
        }
        return id != null && agentId.toString().equals(String.valueOf(id));
    }

    private Map<String, Object> toHistory(AuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("action", log.getAction());
        row.put("createdAt", log.getCreatedAt());
        if (log.getAgent() != null) {
            row.put("auteur", (log.getAgent().getPrenom() + " " + log.getAgent().getNom()).trim());
        }
        Map<String, Object> details = log.getValeurApres() != null ? log.getValeurApres() : log.getValeurAvant();
        if (details != null) {
            row.put("role", details.get("role"));
            row.put("project", details.get("project"));
            row.put("cible", details.get("cible"));
        }
        return row;
    }

    public record ProjectAccess(Long projectId, Long roleId) {}
}
