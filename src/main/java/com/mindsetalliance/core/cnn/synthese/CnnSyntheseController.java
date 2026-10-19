package com.mindsetalliance.core.cnn.synthese;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.EffectivePermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Tableau de bord Colis na Nga de MA Workspace : synthèse de toutes les données de l'application. */
@RestController
@RequestMapping("/api/cnn")
public class CnnSyntheseController {

    private static final String PROJECT = "CNN";
    private static final String PERMISSION = "VIEW_CNN_DASHBOARD";

    private final CnnSyntheseService service;
    private final EffectivePermissionService permissions;

    public CnnSyntheseController(CnnSyntheseService service, EffectivePermissionService permissions) {
        this.service = service;
        this.permissions = permissions;
    }

    @GetMapping("/synthese")
    public Map<String, Object> synthese() {
        Jwt jwt = JwtRoles.currentJwt();
        Long agentId = JwtRoles.agentId();
        boolean allowed = agentId != null
                && JwtRoles.canSeeProject(PROJECT)
                && !permissions.isDenied(agentId, PERMISSION, PROJECT)
                && (JwtRoles.hasFullAccess(jwt) || permissions.hasPermission(agentId, PERMISSION, PROJECT));
        if (!allowed) {
            throw new AccessDeniedException("Permission insuffisante");
        }
        return service.synthese();
    }
}
