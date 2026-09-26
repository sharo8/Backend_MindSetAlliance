package com.mindsetalliance.core.common.security;

import com.mindsetalliance.core.iam.EffectivePermissionService;
import com.mindsetalliance.core.iam.RoleRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class RoleGuardAspect {

    private final RoleRepository roleRepository;
    private final EffectivePermissionService effectivePermissionService;

    public RoleGuardAspect(RoleRepository roleRepository, EffectivePermissionService effectivePermissionService) {
        this.roleRepository = roleRepository;
        this.effectivePermissionService = effectivePermissionService;
    }

    @Around("@annotation(require)")
    public Object checkProjectRole(ProceedingJoinPoint joinPoint, RequireProjectRole require) throws Throwable {
        Jwt jwt = JwtRoles.currentJwt();
        if (JwtRoles.hasFullAccess(jwt)) {
            return joinPoint.proceed();
        }
        if (!JwtRoles.hasProjectRole(jwt, require.project(), Arrays.asList(require.roles()))) {
            throw new AccessDeniedException("Rôle projet insuffisant");
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(require)")
    public Object checkRoles(ProceedingJoinPoint joinPoint, RequireRoles require) throws Throwable {
        Jwt jwt = JwtRoles.currentJwt();
        if (JwtRoles.hasFullAccess(jwt)) {
            return joinPoint.proceed();
        }
        if (!JwtRoles.hasAnyRole(jwt, Arrays.asList(require.value()))) {
            throw new AccessDeniedException("Rôle insuffisant");
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(require)")
    public Object checkPermissions(ProceedingJoinPoint joinPoint, RequirePermissions require) throws Throwable {
        Jwt jwt = JwtRoles.currentJwt();
        Long agentId = JwtRoles.agentId();
        if (agentId == null) {
            throw new AccessDeniedException("Permission insuffisante");
        }
        boolean allowed = Arrays.stream(require.value()).anyMatch(code -> {
            if (effectivePermissionService.isDenied(agentId, code, null)) {
                return false;
            }
            if (JwtRoles.hasFullAccess(jwt)) {
                return true;
            }
            return effectivePermissionService.hasPermission(agentId, code, null);
        });
        if (!allowed) {
            throw new AccessDeniedException("Permission insuffisante");
        }
        return joinPoint.proceed();
    }
}
