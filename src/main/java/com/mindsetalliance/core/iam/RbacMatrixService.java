package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.notifications.RoleMatrixNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RbacMatrixService {

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
    private final AgentRoleRepository agentRoleRepository;
    private final AuditService auditService;
    private final RoleMatrixNotifier roleMatrixNotifier;

    public RbacMatrixService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                             AgentRoleRepository agentRoleRepository, AuditService auditService,
                             RoleMatrixNotifier roleMatrixNotifier) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.auditService = auditService;
        this.roleMatrixNotifier = roleMatrixNotifier;
    }

    public String roleLibelle(String nom) {
        return ROLE_LABELS.getOrDefault(nom, nom);
    }

    public long agentsCount(Long roleId) {
        return agentRoleRepository.countDistinctAgentsByRoleId(roleId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(List<RolePermissionPatch> patches) {
        List<RoleMatrixImpact.RoleDelta> deltas = computeDeltas(patches);
        List<RoleMatrixImpact.AgentDigest> digests = digestsFor(deltas);
        return toImpactBody(deltas, digests, false);
    }

    @Transactional
    public Map<String, Object> save(List<RolePermissionPatch> patches) {
        List<RoleMatrixImpact.RoleDelta> deltas = computeDeltas(patches);
        apply(patches);
        List<RoleMatrixImpact.AgentDigest> digests = digestsFor(deltas);
        roleMatrixNotifier.notifyAfterCommit(digests);
        auditService.record(JwtRoles.agentId(), "UPDATE_ROLE_PERMISSIONS_MATRIX", "ROLE", null, null,
                Map.of("rolesChanged", deltas.size(), "agentsNotified", digests.size()));
        return toImpactBody(deltas, digests, true);
    }

    private List<RoleMatrixImpact.RoleDelta> computeDeltas(List<RolePermissionPatch> patches) {
        List<RoleMatrixImpact.RoleDelta> deltas = new ArrayList<>();
        for (RolePermissionPatch patch : patches == null ? List.<RolePermissionPatch>of() : patches) {
            if (patch == null || patch.id() == null) {
                continue;
            }
            Role role = roleRepository.findById(patch.id())
                    .orElseThrow(() -> new BusinessException("Rôle introuvable", 404));
            Set<String> current = role.getPermissions().stream().map(Permission::getCode)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> next = new LinkedHashSet<>(patch.permissions() == null ? List.of() : patch.permissions());
            List<String> addedCodes = RoleMatrixImpact.onlyIn(next, current);
            List<String> removedCodes = RoleMatrixImpact.onlyIn(current, next);
            if (addedCodes.isEmpty() && removedCodes.isEmpty()) {
                continue;
            }
            deltas.add(new RoleMatrixImpact.RoleDelta(
                    role.getId(),
                    role.getNom(),
                    roleLibelle(role.getNom()),
                    addedCodes.stream().map(code -> new RoleMatrixImpact.PermLine(code, permLabel(code), true)).toList(),
                    removedCodes.stream().map(code -> new RoleMatrixImpact.PermLine(code, permLabel(code), false)).toList()
            ));
        }
        return deltas;
    }

    private void apply(List<RolePermissionPatch> patches) {
        for (RolePermissionPatch patch : patches == null ? List.<RolePermissionPatch>of() : patches) {
            if (patch == null || patch.id() == null) {
                continue;
            }
            Role role = roleRepository.findById(patch.id())
                    .orElseThrow(() -> new BusinessException("Rôle introuvable", 404));
            Set<Permission> next = new HashSet<>();
            for (String code : patch.permissions() == null ? List.<String>of() : patch.permissions()) {
                next.add(permissionRepository.findByCode(code)
                        .orElseThrow(() -> new BusinessException("Permission introuvable : " + code)));
            }
            role.getPermissions().clear();
            role.getPermissions().addAll(next);
        }
    }

    private List<RoleMatrixImpact.AgentDigest> digestsFor(List<RoleMatrixImpact.RoleDelta> deltas) {
        if (deltas.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = deltas.stream().map(RoleMatrixImpact.RoleDelta::roleId).toList();
        return RoleMatrixImpact.groupByAgent(agentRoleRepository.findWithAgentByRoleIds(roleIds), deltas);
    }

    private Map<String, Object> toImpactBody(List<RoleMatrixImpact.RoleDelta> deltas,
                                             List<RoleMatrixImpact.AgentDigest> digests,
                                             boolean saved) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("saved", saved);
        body.put("agentsAffected", digests.size());
        body.put("emailsQueued", saved ? digests.size() : 0);
        body.put("roles", deltas.stream().map(delta -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", delta.roleId());
            row.put("nom", delta.roleNom());
            row.put("libelle", delta.roleLibelle());
            row.put("agentsCount", agentRoleRepository.countDistinctAgentsByRoleId(delta.roleId()));
            row.put("added", delta.added().stream().map(this::permMap).toList());
            row.put("removed", delta.removed().stream().map(this::permMap).toList());
            return row;
        }).toList());
        List<String> roleNames = deltas.stream().map(RoleMatrixImpact.RoleDelta::roleLibelle).toList();
        String rolesTxt = roleNames.isEmpty()
                ? ""
                : roleNames.size() == 1
                ? " ayant le rôle " + roleNames.getFirst()
                : " ayant les rôles " + String.join(", ", roleNames);
        int n = digests.size();
        String confirm = n == 0
                ? "Aucun agent actif n’est rattaché aux rôles modifiés. Confirmer l’enregistrement ?"
                : "Cette modification affectera " + n + " agent" + (n > 1 ? "s" : "")
                + rolesTxt
                + ". Un e-mail de notification leur sera envoyé. Confirmer ?";
        body.put("confirmation", confirm);
        body.put("message", saved
                ? "Matrice de permissions enregistrée. " + n + " notification" + (n > 1 ? "s" : "") + " en file."
                : "Aperçu des changements.");
        return body;
    }

    private Map<String, Object> permMap(RoleMatrixImpact.PermLine line) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", line.code());
        row.put("libelle", line.libelle());
        return row;
    }

    private String permLabel(String code) {
        return permissionRepository.findByCode(code)
                .map(p -> p.getLibelle() == null || p.getLibelle().isBlank() ? code : p.getLibelle())
                .orElse(code);
    }

    public record RolePermissionPatch(Long id, List<String> permissions) {}
}
