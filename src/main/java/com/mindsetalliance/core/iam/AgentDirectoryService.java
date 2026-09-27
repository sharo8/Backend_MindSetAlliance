package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditLog;
import com.mindsetalliance.core.audit.AuditLogRepository;
import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.auth.AuthService;
import com.mindsetalliance.core.auth.RefreshTokenRepository;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.notifications.AgentChangeNotifier;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentDirectoryService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private final AgentRepository agentRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;
    private final DepartementRepository departementRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AuthService authService;
    private final PermissionRepository permissionRepository;
    private final AgentPermissionOverrideRepository overrideRepository;
    private final EffectivePermissionService effectivePermissionService;
    private final AgentChangeNotifier agentChangeNotifier;
    private final PermissionActionService permissionActionService;
    private final AuditLogRepository auditLogRepository;
    private final SecureRandom random = new SecureRandom();

    public AgentDirectoryService(AgentRepository agentRepository, RoleRepository roleRepository,
                                 ProjectRepository projectRepository, DepartementRepository departementRepository,
                                 AgentRoleRepository agentRoleRepository, RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder, AuditService auditService, AuthService authService,
                                 PermissionRepository permissionRepository,
                                 AgentPermissionOverrideRepository overrideRepository,
                                 EffectivePermissionService effectivePermissionService,
                                 AgentChangeNotifier agentChangeNotifier,
                                 PermissionActionService permissionActionService,
                                 AuditLogRepository auditLogRepository) {
        this.agentRepository = agentRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
        this.departementRepository = departementRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.authService = authService;
        this.permissionRepository = permissionRepository;
        this.overrideRepository = overrideRepository;
        this.effectivePermissionService = effectivePermissionService;
        this.agentChangeNotifier = agentChangeNotifier;
        this.permissionActionService = permissionActionService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(String q, Long departementId, String statut, Pageable pageable) {
        return list(q, departementId, statut, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(String q, Long departementId, String statut, String roleNom, String scope,
                                          Pageable pageable) {
        Specification<Agent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nom")), like),
                        cb.like(cb.lower(root.get("prenom")), like),
                        cb.like(cb.lower(root.get("emailPro")), like)
                ));
            }
            if (departementId != null) {
                Join<Agent, Departement> dep = root.join("departement", JoinType.LEFT);
                predicates.add(cb.equal(dep.get("id"), departementId));
            }
            if (statut != null && !statut.isBlank()) {
                predicates.add(cb.equal(root.get("statut"), statut.trim().toUpperCase()));
            }
            if (roleNom != null && !roleNom.isBlank()) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<AgentRole> ar = sq.from(AgentRole.class);
                sq.select(ar.get("id"));
                sq.where(
                        cb.equal(ar.get("agent").get("id"), root.get("id")),
                        cb.equal(ar.get("role").get("nom"), roleNom.trim().toUpperCase())
                );
                predicates.add(cb.exists(sq));
            }
            if (scope != null && !scope.isBlank()) {
                String normalized = scope.trim().toUpperCase();
                Subquery<Long> wide = query.subquery(Long.class);
                Root<AgentRole> wideRoot = wide.from(AgentRole.class);
                wide.select(wideRoot.get("id"));
                wide.where(cb.equal(wideRoot.get("agent").get("id"), root.get("id")), cb.isNull(wideRoot.get("project")));
                if ("ENTREPRISE".equals(normalized)) {
                    predicates.add(cb.exists(wide));
                } else if ("PROJECT".equals(normalized) || "PROJET".equals(normalized)) {
                    Subquery<Long> scoped = query.subquery(Long.class);
                    Root<AgentRole> scopedRoot = scoped.from(AgentRole.class);
                    scoped.select(scopedRoot.get("id"));
                    scoped.where(cb.equal(scopedRoot.get("agent").get("id"), root.get("id")), cb.isNotNull(scopedRoot.get("project")));
                    predicates.add(cb.and(cb.exists(scoped), cb.not(cb.exists(wide))));
                } else {
                    Subquery<Long> byProject = query.subquery(Long.class);
                    Root<AgentRole> projectRoot = byProject.from(AgentRole.class);
                    Join<AgentRole, Project> project = projectRoot.join("project");
                    byProject.select(projectRoot.get("id"));
                    Predicate codeMatch = cb.equal(cb.upper(project.get("code")), normalized);
                    if (normalized.chars().allMatch(Character::isDigit)) {
                        codeMatch = cb.or(codeMatch, cb.equal(project.get("id"), Long.parseLong(normalized)));
                    }
                    byProject.where(cb.equal(projectRoot.get("agent").get("id"), root.get("id")), codeMatch);
                    predicates.add(cb.exists(byProject));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return agentRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request) {
        if (agentRepository.findByEmailProIgnoreCase(request.emailPro()).isPresent()) {
            throw new BusinessException("Un agent existe déjà avec cet e-mail professionnel");
        }
        String temporary = generatePassword();
        Agent agent = new Agent();
        agent.setEmailPro(request.emailPro().trim());
        agent.setPasswordHash(passwordEncoder.encode(temporary));
        agent.setNom(request.nom().trim());
        agent.setPrenom(request.prenom().trim());
        agent.setTelephone(blankToNull(request.telephone()));
        agent.setStatut("ACTIF");
        agent.setDoitChangerMotDePasse(true);
        agent.setDepartement(requireDepartement(request.departementId(), true));
        if (request.photo() != null && !request.photo().isBlank()) {
            PhotoProfilUtil.apply(agent, request.photo());
        }
        agentRepository.save(agent);
        replaceRoles(agent, request.roles());
        boolean mailSent = authService.envoyerBienvenueSiNecessaire(agent.getEmailPro());
        auditService.record(JwtRoles.agentId(), "CREATE", "AGENT", agent.getId(), null,
                Map.of("email", agent.getEmailPro(), "entite", agent.getPrenom() + " " + agent.getNom()));
        Map<String, Object> body = toDto(agent);
        body.put("motDePasseTemporaire", temporary);
        body.put("emailBienvenueEnvoye", mailSent);
        body.put("message", mailSent
                ? "Agent enregistré. Un e-mail de bienvenue a été envoyé pour définir son mot de passe."
                : "Agent enregistré. Transmettez le mot de passe temporaire une seule fois (e-mail de bienvenue non envoyé).");
        return body;
    }

    @Transactional
    public Map<String, Object> update(Long id, UpdateRequest request) {
        assertNotSelf(id);
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        String before = agentChangeNotifier.snapshot(id);
        Map<String, Object> avant = Map.of("nom", String.valueOf(agent.getNom()), "prenom", String.valueOf(agent.getPrenom()));
        if (request.nom() != null) {
            if (request.nom().isBlank()) {
                throw new BusinessException("Le nom est obligatoire");
            }
            agent.setNom(request.nom().trim());
        }
        if (request.prenom() != null) {
            if (request.prenom().isBlank()) {
                throw new BusinessException("Le prénom est obligatoire");
            }
            agent.setPrenom(request.prenom().trim());
        }
        if (request.telephone() != null) {
            agent.setTelephone(blankToNull(request.telephone()));
        }
        if (request.departementId() != null) {
            agent.setDepartement(requireDepartement(request.departementId(), false));
        }
        if (request.photo() != null) {
            if (request.photo().isBlank()) {
                agent.setPhotoProfil(null);
                agent.setPhotoMime(null);
            } else {
                PhotoProfilUtil.apply(agent, request.photo());
            }
        }
        if (request.roles() != null) {
            replaceRoles(agent, request.roles());
        }
        agentChangeNotifier.notifyIfChanged(id, before);
        auditService.record(JwtRoles.agentId(), "UPDATE", "AGENT", id, avant,
                Map.of("nom", agent.getNom(), "prenom", agent.getPrenom(), "entite", agent.getPrenom() + " " + agent.getNom()));
        return toDto(agent);
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, String statut) {
        return changeStatus(id, statut, null);
    }

    @Transactional
    public Map<String, Object> desactiver(Long id, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de désactivation est obligatoire");
        }
        Map<String, Object> body = changeStatus(id, "INACTIF", motif.trim(), "DISABLE");
        agentChangeNotifier.notifyDeactivated(id);
        return body;
    }

    @Transactional
    public Map<String, Object> supprimer(Long id, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de suppression est obligatoire");
        }
        Map<String, Object> body = changeStatus(id, "INACTIF", motif.trim(), "DELETE");
        agentChangeNotifier.notifyDeactivated(id);
        body.put("message", "Agent retiré de l’annuaire (aucune suppression physique).");
        return body;
    }

    @Transactional
    public Map<String, Object> reactiver(Long id) {
        return changeStatus(id, "ACTIF", null);
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, String statut, String motif) {
        return changeStatus(id, statut, motif, null);
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, String statut, String motif, String auditAction) {
        assertNotSelf(id);
        String normalized = statut == null ? "" : statut.trim().toUpperCase();
        if ("DESACTIVE".equals(normalized) || "SUSPENDU".equals(normalized) || "SUPPRIME".equals(normalized)) {
            normalized = "INACTIF";
        }
        if (!List.of("ACTIF", "INACTIF").contains(normalized)) {
            throw new BusinessException("Statut agent invalide (ACTIF ou INACTIF)");
        }
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        String before = agent.getStatut();
        agent.setStatut(normalized);
        if ("INACTIF".equals(normalized)) {
            refreshTokenRepository.findByAgentIdAndRevokedFalse(id).forEach(token -> token.setRevoked(true));
        }
        String action = auditAction != null && !auditAction.isBlank()
                ? auditAction
                : ("INACTIF".equals(normalized) ? "DISABLE" : "ENABLE");
        auditService.record(JwtRoles.agentId(), action, "AGENT", id,
                Map.of("statut", before),
                motif == null
                        ? Map.of("statut", normalized, "entite", agent.getPrenom() + " " + agent.getNom())
                        : Map.of("statut", normalized, "motif", motif, "entite", agent.getPrenom() + " " + agent.getNom()));
        return toDto(agent);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        Agent agent = agentRepository.findById(id).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Map<String, Object> body = toDto(agent);
        List<Map<String, Object>> overrides = overrideRepository.findByAgentId(id).stream().map(this::toOverride).toList();
        body.put("overrides", overrides);
        body.put("permissionsEffectives", effectivePermissionService.calculerPermissionsEffectives(id, null).stream().sorted().toList());
        java.util.Map<String, java.util.List<String>> byProject = new LinkedHashMap<>();
        for (Project project : projectRepository.findAll()) {
            if (project.getCode() == null || "INACTIF".equalsIgnoreCase(project.getStatut())) {
                continue;
            }
            byProject.put(project.getCode(), effectivePermissionService.calculerPermissionsEffectives(id, project.getCode()).stream().sorted().toList());
        }
        body.put("permissionsEffectivesParProjet", byProject);
        body.put("permissionActions", permissionActionService.listForAgent(id));
        auditLogRepository.findFirstByObjetTypeAndObjetIdAndActionInOrderByCreatedAtDesc(
                "AGENT", id, List.of("UPDATE", "DISABLE", "ENABLE", "DELETE")).ifPresent(log -> {
            Map<String, Object> last = new LinkedHashMap<>();
            last.put("id", log.getId());
            last.put("action", log.getAction());
            last.put("createdAt", log.getCreatedAt());
            if (log.getAgent() != null) {
                last.put("auteur", (log.getAgent().getPrenom() + " " + log.getAgent().getNom()).trim());
                last.put("auteurEmail", log.getAgent().getEmailPro());
            }
            body.put("derniereModification", last);
        });
        return body;
    }

    @Transactional(readOnly = true)
    public java.util.List<String> permissionsEffectives(Long id, String projectCode) {
        agentRepository.findById(id).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        return effectivePermissionService.calculerPermissionsEffectives(id, projectCode).stream().sorted().toList();
    }

    @Transactional
    public Map<String, Object> addOverride(Long agentId, Long permissionId, Long projectId, String type, String motif) {
        assertNotSelf(agentId);
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!List.of("GRANT", "DENY").contains(normalized)) {
            throw new BusinessException("Type de dérogation invalide (GRANT ou DENY)");
        }
        if ("DENY".equals(normalized) && (motif == null || motif.isBlank())) {
            throw new BusinessException("Le motif est obligatoire pour une restriction DENY");
        }
        String resolvedMotif = (motif == null || motif.isBlank())
                ? "Personnalisation des permissions (Accès agent)"
                : motif.trim();
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new BusinessException("Permission introuvable", 404));
        Project project = null;
        if (projectId != null) {
            project = projectRepository.findById(projectId).orElseThrow(() -> new BusinessException("Projet introuvable", 404));
        }
        AgentPermissionOverride existing = overrideRepository.findDuplicate(agentId, permissionId, projectId).orElse(null);
        Map<String, Object> avant = existing == null ? null : Map.of("type", existing.getType(), "motif", existing.getMotif());
        AgentPermissionOverride override = existing == null ? new AgentPermissionOverride() : existing;
        override.setAgent(agent);
        override.setPermission(permission);
        override.setProject(project);
        override.setType(normalized);
        override.setMotif(resolvedMotif);
        override.setAccordePar(agentRepository.findById(JwtRoles.agentId())
                .orElseThrow(() -> new BusinessException("Agent connecté introuvable", 401)));
        overrideRepository.save(override);
        auditService.record(JwtRoles.agentId(), "OVERRIDE_" + normalized, "AGENT_PERMISSION", override.getId(),
                avant,
                Map.of("permission", permission.getCode(), "type", normalized, "motif", resolvedMotif, "cibleId", agentId));
        Map<String, Object> body = toOverride(override);
        body.put("message", "GRANT".equals(normalized) ? "Permission individuelle accordée." : "Restriction individuelle enregistrée.");
        return body;
    }

    @Transactional
    public Map<String, Object> removeOverride(Long agentId, Long overrideId) {
        assertNotSelf(agentId);
        AgentPermissionOverride override = overrideRepository.findByIdAndAgentId(overrideId, agentId)
                .orElseThrow(() -> new BusinessException("Dérogation introuvable", 404));
        String code = override.getPermission().getCode();
        String type = override.getType();
        overrideRepository.delete(override);
        auditService.record(JwtRoles.agentId(), "OVERRIDE_REMOVE", "AGENT_PERMISSION", overrideId,
                Map.of("permission", code, "type", type, "cibleId", agentId), Map.of("removed", true));
        return Map.of("message", "Dérogation retirée.", "id", overrideId);
    }

    private void assertNotSelf(Long targetAgentId) {
        Long current = JwtRoles.agentId();
        if (current != null && current.equals(targetAgentId)) {
            throw new BusinessException("Vous ne pouvez pas modifier vos propres rôles, permissions ou statut.", 403);
        }
    }

    private Map<String, Object> toOverride(AgentPermissionOverride override) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", override.getId());
        row.put("type", override.getType());
        row.put("motif", override.getMotif());
        row.put("createdAt", override.getCreatedAt());
        if (override.getPermission() != null) {
            row.put("permissionId", override.getPermission().getId());
            row.put("permission", override.getPermission().getCode());
            row.put("permissionLibelle", override.getPermission().getLibelle());
        }
        row.put("projectId", override.getProject() == null ? null : override.getProject().getId());
        row.put("projectCode", override.getProject() == null ? null : override.getProject().getCode());
        if (override.getAccordePar() != null) {
            row.put("accordePar", override.getAccordePar().getPrenom() + " " + override.getAccordePar().getNom());
        }
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDepartements() {
        return departementRepository.findAllWithRoles().stream()
                .sorted(java.util.Comparator.comparing(Departement::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(dep -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", dep.getId());
            row.put("code", dep.getCode());
            row.put("nom", dep.getNom());
            row.put("description", dep.getDescription());
            row.put("statut", dep.getStatut() == null ? "ACTIF" : dep.getStatut());
            row.put("roles", dep.getRolesDefaut().stream().map(role -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", role.getId());
                r.put("nom", role.getNom());
                return r;
            }).toList());
            return row;
        }).toList();
    }

    private boolean resolveHasPhoto(Agent agent) {
        if (org.hibernate.Hibernate.isPropertyInitialized(agent, "photoProfil")) {
            return PhotoProfilUtil.hasPhoto(agent);
        }
        return agent.isHasPhoto();
    }

    private Departement requireDepartement(Long departementId, boolean mustBeActive) {
        if (departementId == null) {
            throw new BusinessException("Le département est obligatoire");
        }
        Departement dep = departementRepository.findById(departementId)
                .orElseThrow(() -> new BusinessException("Département introuvable"));
        if (mustBeActive && "INACTIF".equals(dep.getStatut())) {
            throw new BusinessException("Ce département n’est plus proposé pour un nouvel agent");
        }
        return dep;
    }

    private void replaceRoles(Agent agent, List<RoleAssign> roles) {
        if (roles == null) {
            return;
        }
        assertNotSelf(agent.getId());
        agentRoleRepository.deleteByAgentId(agent.getId());
        for (RoleAssign assign : roles) {
            if (assign == null || assign.role() == null || assign.role().isBlank()) {
                continue;
            }
            Role role = roleRepository.findByNom(assign.role().trim().toUpperCase())
                    .orElseThrow(() -> new BusinessException("Rôle introuvable : " + assign.role()));
            Project project = null;
            if (assign.projectCode() != null && !assign.projectCode().isBlank()) {
                project = projectRepository.findByCode(assign.projectCode())
                        .orElseThrow(() -> new BusinessException("Projet introuvable"));
            }
            AgentRole entity = new AgentRole();
            entity.setAgent(agent);
            entity.setRole(role);
            entity.setProject(project);
            entity.setDateDebut(LocalDate.now());
            agentRoleRepository.save(entity);
        }
    }

    private Map<String, Object> toDto(Agent agent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", agent.getId());
        body.put("nom", agent.getNom());
        body.put("prenom", agent.getPrenom());
        body.put("emailPro", agent.getEmailPro());
        body.put("telephone", agent.getTelephone());
        body.put("statut", agent.getStatut());
        body.put("createdAt", agent.getCreatedAt());
        if (agent.getDepartement() != null) {
            body.put("departement", Map.of(
                    "id", agent.getDepartement().getId(),
                    "nom", agent.getDepartement().getNom(),
                    "code", agent.getDepartement().getCode()
            ));
        } else {
            body.put("departement", null);
        }
        body.put("roles", agentRoleRepository.findByAgentId(agent.getId()).stream().map(ar -> {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("id", ar.getId());
            role.put("role", ar.getRole().getNom());
            role.put("projectCode", ar.getProject() == null ? null : ar.getProject().getCode());
            role.put("projectNom", ar.getProject() == null ? null : ar.getProject().getNom());
            return role;
        }).toList());
        body.put("hasPhoto", resolveHasPhoto(agent));
        body.put("hasOverrides", overrideRepository.countByAgentId(agent.getId()) > 0);
        return body;
    }

    private String generatePassword() {
        StringBuilder builder = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            builder.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return builder.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateRequest(String nom, String prenom, String emailPro, String telephone,
                                Long departementId, List<RoleAssign> roles, String photo) {}

    public record UpdateRequest(String nom, String prenom, String emailPro, String telephone,
                                Long departementId, List<RoleAssign> roles, String photo) {}

    public record RoleAssign(String role, String projectCode) {}
}
