package com.mindsetalliance.core.iam;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleMatrixImpactTest {

    @Test
    void groupsTwoRolesIntoOneDigestPerAgent() {
        Role finance = role(1L, "FINANCE");
        Role rh = role(2L, "RH");
        Agent agent = agent(12L, "finance.demo@mindsetalliance.com", "Léa", "Finance");

        AgentRole a1 = assignment(agent, finance);
        AgentRole a2 = assignment(agent, rh);

        RoleMatrixImpact.RoleDelta dFinance = new RoleMatrixImpact.RoleDelta(
                1L, "FINANCE", "Finance",
                List.of(new RoleMatrixImpact.PermLine("MANAGE_INTERNAL_AGENTS", "Gérer les agents internes", true)),
                List.of(new RoleMatrixImpact.PermLine("VIEW_PAYROLL", "Consulter la paie", false)));
        RoleMatrixImpact.RoleDelta dRh = new RoleMatrixImpact.RoleDelta(
                2L, "RH", "RH",
                List.of(),
                List.of(new RoleMatrixImpact.PermLine("MANAGE_USERS", "Gérer les utilisateurs", false)));

        List<RoleMatrixImpact.AgentDigest> digests =
                RoleMatrixImpact.groupByAgent(List.of(a1, a2), List.of(dFinance, dRh));

        assertEquals(1, digests.size());
        assertEquals(12L, digests.getFirst().agentId());
        assertEquals(2, digests.getFirst().roles().size());
    }

    @Test
    void skipsInactiveAgents() {
        Role finance = role(1L, "FINANCE");
        Agent agent = agent(3L, "off@example.com", "Off", "Line");
        agent.setStatut("INACTIF");
        RoleMatrixImpact.RoleDelta delta = new RoleMatrixImpact.RoleDelta(
                1L, "FINANCE", "Finance",
                List.of(new RoleMatrixImpact.PermLine("VIEW_FINANCE", "Voir la finance", true)),
                List.of());
        assertEquals(0, RoleMatrixImpact.groupByAgent(List.of(assignment(agent, finance)), List.of(delta)).size());
    }

    private static Role role(Long id, String nom) {
        Role role = new Role();
        role.setNom(nom);
        try {
            var field = Role.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(role, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return role;
    }

    private static Agent agent(Long id, String email, String prenom, String nom) {
        Agent agent = new Agent();
        agent.setEmailPro(email);
        agent.setPrenom(prenom);
        agent.setNom(nom);
        agent.setStatut("ACTIF");
        try {
            var field = Agent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(agent, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return agent;
    }

    private static AgentRole assignment(Agent agent, Role role) {
        AgentRole row = new AgentRole();
        row.setAgent(agent);
        row.setRole(role);
        return row;
    }
}
