package com.mindsetalliance.core.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class JwtRoles {

    private JwtRoles() {
    }

    public static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new org.springframework.security.access.AccessDeniedException("Jeton manquant");
        }
        return jwt;
    }

    public static Long agentId() {
        String sub = currentJwt().getSubject();
        return Long.parseLong(sub.replace("agent-id-", ""));
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> roleClaims(Jwt jwt) {
        Object claim = jwt.getClaim("roles");
        if (claim instanceof List<?> list) {
            return list.stream().map(item -> (Map<String, Object>) item).toList();
        }
        return List.of();
    }

    public static boolean hasProjectRole(Jwt jwt, String projectCode, Collection<String> allowedRoles) {
        Set<String> allowed = allowedRoles.stream().map(String::toUpperCase).collect(Collectors.toSet());
        return roleClaims(jwt).stream().anyMatch(role -> {
            Object project = role.get("projectCode");
            Object name = role.get("role");
            if (name == null) {
                return false;
            }
            boolean projectOk = project == null || projectCode.equalsIgnoreCase(String.valueOf(project));
            return projectOk && allowed.contains(String.valueOf(name).toUpperCase());
        });
    }

    public static Set<String> roleNames(Jwt jwt) {
        return roleClaims(jwt).stream()
                .map(role -> role.get("role"))
                .filter(name -> name != null)
                .map(name -> String.valueOf(name).toUpperCase())
                .collect(Collectors.toSet());
    }

    /**
     * Accès total ADMIN_SYSTEME / DIRECTION — à garder identique au frontend (lib/rbac/permissions.ts).
     * Le backend reste la source d’autorité ; le menu ne fait que refléter cette règle.
     */
    public static boolean hasFullAccess(Jwt jwt) {
        return roleNames(jwt).stream().anyMatch(name -> name.equals("ADMIN_SYSTEME") || name.equals("DIRECTION"));
    }

    public static boolean hasAnyRole(Jwt jwt, Collection<String> allowedRoles) {
        Set<String> allowed = allowedRoles.stream().map(String::toUpperCase).collect(Collectors.toSet());
        return roleClaims(jwt).stream()
                .map(role -> role.get("role"))
                .filter(name -> name != null)
                .anyMatch(name -> allowed.contains(String.valueOf(name).toUpperCase()));
    }

    public static Set<String> visibleProjectCodes(Jwt jwt) {
        boolean companyWide = roleClaims(jwt).stream().anyMatch(role -> role.get("projectCode") == null);
        if (companyWide) {
            return Set.of("*");
        }
        return roleClaims(jwt).stream()
                .map(role -> role.get("projectCode"))
                .filter(code -> code != null)
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    public static boolean canSeeProject(String projectCode) {
        Set<String> codes = visibleProjectCodes(currentJwt());
        if (codes.contains("*")) {
            return true;
        }
        if (projectCode == null || projectCode.isBlank() || "ENTREPRISE".equalsIgnoreCase(projectCode)) {
            return true;
        }
        return codes.stream().anyMatch(code -> code.equalsIgnoreCase(projectCode));
    }

    public static void assertCanSeeProject(String projectCode) {
        if (!canSeeProject(projectCode)) {
            throw new org.springframework.security.access.AccessDeniedException("Périmètre société non autorisé");
        }
    }
}
