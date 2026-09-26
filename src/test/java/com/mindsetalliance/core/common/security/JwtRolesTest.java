package com.mindsetalliance.core.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtRolesTest {

    @Test
    void roleProjetEtRoleEntreprise() {
        Jwt cnnOnly = jwt(List.of(Map.of("projectCode", "CNN", "role", "SUPPORT")));
        assertTrue(JwtRoles.hasProjectRole(cnnOnly, "CNN", List.of("SUPPORT", "FINANCE")));
        assertFalse(JwtRoles.hasProjectRole(cnnOnly, "MDR", List.of("SUPPORT", "MARKETING")));

        Jwt entreprise = jwt(List.of(new java.util.HashMap<>() {{
            put("projectCode", null);
            put("role", "DIRECTION");
        }}));
        assertTrue(JwtRoles.hasProjectRole(entreprise, "MDR", List.of("DIRECTION")));
        assertTrue(JwtRoles.hasAnyRole(entreprise, List.of("RH", "DIRECTION")));
    }

    private Jwt jwt(List<Map<String, Object>> roles) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("agent-id-1")
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
