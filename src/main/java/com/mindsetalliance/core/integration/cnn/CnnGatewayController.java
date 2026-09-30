package com.mindsetalliance.core.integration.cnn;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.AgentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeSet;

/**
 * Relais sécurisé MA Workspace → Colis na Nga.
 *
 * La console n'appelle jamais Colis na Nga directement : elle appelle
 * {@code /api/cnn/<chemin>} sur le Core, qui
 * <ol>
 *   <li>vérifie le jeton de session de l'agent (chaîne de sécurité standard) ;</li>
 *   <li>vérifie que l'agent est toujours ACTIF en base — une désactivation coupe
 *       l'accès immédiatement, sans attendre l'expiration du jeton ;</li>
 *   <li>émet un jeton court à audience {@code colis-na-nga} et relaie vers
 *       {@code <CNN>/api/ma/<chemin>}.</li>
 * </ol>
 * Colis na Nga revérifie signature, audience et rôles : deux contrôles indépendants.
 */
@RestController
@RequestMapping("/api/cnn")
public class CnnGatewayController {

    private static final String PREFIX = "/api/cnn";

    private final CnnIntegrationProperties properties;
    private final CnnClient client;
    private final CnnServiceTokenIssuer tokenIssuer;
    private final AgentRepository agentRepository;
    private final AuditService auditService;

    public CnnGatewayController(CnnIntegrationProperties properties, CnnClient client,
                                CnnServiceTokenIssuer tokenIssuer, AgentRepository agentRepository,
                                AuditService auditService) {
        this.properties = properties;
        this.client = client;
        this.tokenIssuer = tokenIssuer;
        this.agentRepository = agentRepository;
        this.auditService = auditService;
    }

    @GetMapping("/**")
    public ResponseEntity<?> get(HttpServletRequest request) {
        Jwt jwt = authorize();
        String target = targetPath(request);
        String cacheKey = roleSignature(jwt) + "|" + target;
        return relay(() -> client.get(target, tokenIssuer.forAgent(jwt), cacheKey));
    }

    @PostMapping("/**")
    public ResponseEntity<?> post(HttpServletRequest request, @RequestBody(required = false) String body) {
        Jwt jwt = authorize();
        String target = targetPath(request);
        ResponseEntity<?> response = relay(() -> client.post(target, tokenIssuer.forAgent(jwt),
                body == null ? null : body.getBytes(StandardCharsets.UTF_8)));
        auditService.record(JwtRoles.agentId(), "CNN_ECRITURE", "CNN", null, null,
                Map.of("chemin", target, "statut", response.getStatusCode().value()));
        return response;
    }

    private Jwt authorize() {
        if (!properties.isEnabled()) {
            throw new CnnDisabledException();
        }
        Jwt jwt = JwtRoles.currentJwt();
        JwtRoles.assertCanSeeProject(CnnServiceTokenIssuer.PROJECT_CODE);
        Long agentId = JwtRoles.agentId();
        boolean actif = agentRepository.findById(agentId)
                .map(agent -> "ACTIF".equals(agent.getStatut()))
                .orElse(false);
        if (!actif) {
            throw new org.springframework.security.access.AccessDeniedException("Compte agent inactif");
        }
        return jwt;
    }

    private String targetPath(HttpServletRequest request) {
        String rest = request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length());
        // Refuse toute tentative de sortir de /api/ma (.., //, encodages).
        if (rest.isEmpty() || rest.contains("..") || rest.contains("//") || rest.contains("%") || rest.contains("\\")) {
            throw new org.springframework.security.access.AccessDeniedException("Chemin non autorisé");
        }
        String query = request.getQueryString();
        return "/api/ma" + rest + (query == null ? "" : "?" + query);
    }

    private static String roleSignature(Jwt jwt) {
        TreeSet<String> sorted = new TreeSet<>();
        JwtRoles.roleClaims(jwt).forEach(r -> sorted.add(r.get("projectCode") + ":" + r.get("role")));
        return String.join(",", sorted);
    }

    private ResponseEntity<?> relay(java.util.function.Supplier<CnnClient.CnnResponse> call) {
        try {
            CnnClient.CnnResponse response = call.get();
            return ResponseEntity.status(response.status())
                    .contentType(MediaType.parseMediaType(response.contentType()))
                    .body(response.body());
        } catch (CnnClient.CnnUnavailableException e) {
            HttpStatus status = e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    static class CnnDisabledException extends RuntimeException {
        CnnDisabledException() { super("Intégration Colis na Nga désactivée"); }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(CnnDisabledException.class)
    public ResponseEntity<Map<String, Object>> disabled(CnnDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }
}
