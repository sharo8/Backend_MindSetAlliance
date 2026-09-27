package com.mindsetalliance.core.iam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regroupe les diffs de matrice par agent : un destinataire, un e-mail. */
public final class RoleMatrixImpact {

    private RoleMatrixImpact() {}

    public record PermLine(String code, String libelle, boolean added) {}

    public record RoleDelta(
            Long roleId,
            String roleNom,
            String roleLibelle,
            List<PermLine> added,
            List<PermLine> removed
    ) {}

    public record AgentDigest(
            Long agentId,
            String email,
            String nomComplet,
            List<RoleDelta> roles
    ) {}

    public static List<AgentDigest> groupByAgent(List<AgentRole> assignments, List<RoleDelta> deltas) {
        Map<Long, RoleDelta> byRole = new LinkedHashMap<>();
        for (RoleDelta delta : deltas) {
            if (delta.added().isEmpty() && delta.removed().isEmpty()) {
                continue;
            }
            byRole.put(delta.roleId(), delta);
        }
        Map<Long, AgentDigest> byAgent = new LinkedHashMap<>();
        for (AgentRole assignment : assignments) {
            if (assignment == null || !assignment.isActive() || assignment.getRole() == null) {
                continue;
            }
            RoleDelta delta = byRole.get(assignment.getRole().getId());
            if (delta == null) {
                continue;
            }
            Agent agent = assignment.getAgent();
            if (agent == null || agent.getId() == null) {
                continue;
            }
            if ("INACTIF".equalsIgnoreCase(String.valueOf(agent.getStatut()))) {
                continue;
            }
            AgentDigest existing = byAgent.get(agent.getId());
            if (existing == null) {
                existing = new AgentDigest(agent.getId(), agent.getEmailPro(), nomComplet(agent), new ArrayList<>());
                byAgent.put(agent.getId(), existing);
            }
            boolean already = existing.roles().stream().anyMatch(r -> r.roleId().equals(delta.roleId()));
            if (!already) {
                existing.roles().add(delta);
            }
        }
        return List.copyOf(byAgent.values());
    }

    public static List<String> onlyIn(Set<String> left, Set<String> right) {
        return left.stream().filter(code -> !right.contains(code)).sorted().toList();
    }

    private static String nomComplet(Agent agent) {
        return ((agent.getPrenom() == null ? "" : agent.getPrenom()) + " "
                + (agent.getNom() == null ? "" : agent.getNom())).trim();
    }
}
