package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DepartementAdminService {

    private final DepartementRepository departementRepository;
    private final RoleRepository roleRepository;
    private final AgentRepository agentRepository;
    private final AuditService auditService;

    public DepartementAdminService(DepartementRepository departementRepository, RoleRepository roleRepository,
                                   AgentRepository agentRepository, AuditService auditService) {
        this.departementRepository = departementRepository;
        this.roleRepository = roleRepository;
        this.agentRepository = agentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return departementRepository.findAllWithRoles().stream()
                .sorted(Comparator.comparing(Departement::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(String nom, String description, List<String> roleNoms) {
        String name = requireNom(nom);
        assertNomUnique(name, null);
        Departement dep = new Departement();
        dep.setNom(name);
        dep.setCode(nextCode());
        dep.setDescription(blankToNull(description));
        dep.setStatut("ACTIF");
        dep.setRolesDefaut(resolveRoles(roleNoms));
        departementRepository.save(dep);
        auditService.record(JwtRoles.agentId(), "CREATE", "DEPARTEMENT", dep.getId(), null, Map.of("nom", name));
        return toDto(dep);
    }

    @Transactional
    public Map<String, Object> update(Long id, String nom, String description, List<String> roleNoms, String statut) {
        Departement dep = departementRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Département introuvable", 404));
        Map<String, Object> avant = Map.of("nom", String.valueOf(dep.getNom()));
        if (nom != null) {
            String name = requireNom(nom);
            assertNomUnique(name, id);
            dep.setNom(name);
        }
        if (description != null) {
            dep.setDescription(blankToNull(description));
        }
        if (roleNoms != null) {
            dep.setRolesDefaut(resolveRoles(roleNoms));
        }
        if (statut != null && !statut.isBlank()) {
            String normalized = statut.trim().toUpperCase();
            if (!List.of("ACTIF", "INACTIF").contains(normalized)) {
                throw new BusinessException("Statut département invalide (ACTIF ou INACTIF)");
            }
            dep.setStatut(normalized);
        }
        auditService.record(JwtRoles.agentId(), "UPDATE", "DEPARTEMENT", id, avant, Map.of("nom", dep.getNom()));
        return toDto(dep);
    }

    @Transactional
    public Map<String, Object> desactiver(Long id) {
        Departement dep = departementRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Département introuvable", 404));
        if ("INACTIF".equals(dep.getStatut())) {
            return toDto(dep);
        }
        String avant = dep.getStatut();
        dep.setStatut("INACTIF");
        auditService.record(JwtRoles.agentId(), "DISABLE", "DEPARTEMENT", id,
                Map.of("statut", avant), Map.of("statut", "INACTIF"));
        return toDto(dep);
    }

    private Map<String, Object> toDto(Departement dep) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", dep.getId());
        row.put("code", dep.getCode());
        row.put("nom", dep.getNom());
        row.put("description", dep.getDescription());
        row.put("statut", dep.getStatut() == null ? "ACTIF" : dep.getStatut());
        row.put("agentsCount", agentRepository.countByDepartement_Id(dep.getId()));
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Role role : dep.getRolesDefaut()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", role.getId());
            r.put("nom", role.getNom());
            roles.add(r);
        }
        roles.sort((a, b) -> String.valueOf(a.get("nom")).compareTo(String.valueOf(b.get("nom"))));
        row.put("roles", roles);
        return row;
    }

    private Set<Role> resolveRoles(List<String> roleNoms) {
        Set<Role> roles = new HashSet<>();
        if (roleNoms == null) {
            return roles;
        }
        for (String nom : roleNoms) {
            if (nom == null || nom.isBlank()) {
                continue;
            }
            roles.add(roleRepository.findByNom(nom.trim().toUpperCase())
                    .orElseThrow(() -> new BusinessException("Rôle introuvable : " + nom)));
        }
        return roles;
    }

    private void assertNomUnique(String nom, Long excludeId) {
        departementRepository.findByNomIgnoreCase(nom).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new BusinessException("Un département porte déjà ce nom");
            }
        });
    }

    private String requireNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new BusinessException("Le nom du département est obligatoire");
        }
        return nom.trim();
    }

    private String nextCode() {
        int n = (int) departementRepository.count() + 1;
        String code;
        do {
            code = String.format("DEP-%02d", n++);
        } while (departementRepository.existsByCode(code));
        return code;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
