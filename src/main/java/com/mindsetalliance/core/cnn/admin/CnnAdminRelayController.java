package com.mindsetalliance.core.cnn.admin;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.cnn.CnnProperties;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.EffectivePermissionService;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Relais d'administration Colis na Nga.
 *
 * {@code /api/cnn/admin/**} → {@code <backend Colis na Nga>/api/admin/**}, avec les mêmes
 * méthode, paramètres et corps. MA Workspace n'écrit jamais dans la base Colis na Nga :
 * chaque modification passe par l'API de l'application, donc par ses règles métier,
 * ses notifications et son audit.
 *
 * Ici : contrôle des permissions MA (une permission par domaine, action déduite de la
 * méthode HTTP), jeton de relais signé RS256 valable 60 s, audit MA de chaque écriture.
 */
@RestController
@RequestMapping("/api/cnn")
public class CnnAdminRelayController {

    private static final Logger log = LoggerFactory.getLogger(CnnAdminRelayController.class);
    private static final String PREFIX = "/api/cnn/admin";
    private static final String AUDIENCE = "colis-na-nga";

    private final CnnProperties properties;
    private final EffectivePermissionService permissions;
    private final AuditService auditService;
    private final JwtEncoder jwtEncoder;
    private final RSAKey rsaKey;
    private final String issuer;
    private final RestClient restClient;

    public CnnAdminRelayController(CnnProperties properties,
                                   EffectivePermissionService permissions,
                                   AuditService auditService,
                                   JwtEncoder jwtEncoder,
                                   RSAKey rsaKey,
                                   @Value("${ma.auth.issuer}") String issuer) {
        this.properties = properties;
        this.permissions = permissions;
        this.auditService = auditService;
        this.jwtEncoder = jwtEncoder;
        this.rsaKey = rsaKey;
        this.issuer = issuer;
        // Client JDK : gère PATCH (utilisé par l'API admin), contrairement à HttpURLConnection.
        Duration timeout = Duration.ofSeconds(Math.max(5, properties.getApi().getTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** État du relais pour l'interface : configuré ou non, et ce que l'agent peut faire. */
    @GetMapping("/admin-relay/status")
    public Map<String, Object> status() {
        Jwt jwt = JwtRoles.currentJwt();
        Long agentId = JwtRoles.agentId();
        Map<String, Object> scopes = new LinkedHashMap<>();
        for (String code : List.of("VIEW_CNN_DASHBOARD", "MANAGE_CNN_ORDERS", "MANAGE_CNN_AGENTS", "MANAGE_CNN_MOTARDS",
                "MANAGE_CNN_ASSOCIATIONS", "MANAGE_CNN_CLIENTS", "MANAGE_CNN_SUBSCRIPTIONS", "MANAGE_CNN_PAYMENTS",
                "MANAGE_CNN_SETTINGS", "MANAGE_CNN_ADMINS")) {
            Map<String, Boolean> actions = new LinkedHashMap<>();
            for (String action : List.of("VIEW", "CREATE", "UPDATE", "DELETE")) {
                actions.put(action, allowed(jwt, agentId, code, action));
            }
            scopes.put(code, actions);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", properties.getApi().configured());
        // Le backend Colis na Nga accepte-t-il vraiment le relais ? (version déployée + MA_CORE_JWK + MA_RELAY_ENABLED)
        body.put("relayReady", properties.getApi().configured() && relayReady(jwt));
        body.put("relayReason", readyReason);
        body.put("scopes", scopes);
        return body;
    }

    @RequestMapping("/admin/**")
    public ResponseEntity<byte[]> relay(HttpServletRequest request) throws IOException {
        if (!properties.getApi().configured()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Relais Colis na Nga non configuré (MA_CNN_API_BASE_URL).");
        }
        String rawPath = request.getRequestURI().substring(request.getContextPath().length());
        String subPath = rawPath.length() > PREFIX.length() ? rawPath.substring(PREFIX.length()) : "";
        if (subPath.contains("..") || subPath.contains("//")) {
            return error(HttpStatus.BAD_REQUEST, "Chemin invalide.");
        }
        List<String> segments = Arrays.stream(subPath.split("/")).filter(s -> !s.isBlank()).toList();
        String first = segments.isEmpty() ? null : segments.get(0);
        String permission = CnnAdminScope.permissionFor(first);
        String action = CnnAdminScope.actionFor(request.getMethod(), segments.size());
        if (permission == null || action == null) {
            return error(HttpStatus.NOT_FOUND, "Route d'administration Colis na Nga non relayée.");
        }

        Jwt jwt = JwtRoles.currentJwt();
        Long agentId = JwtRoles.agentId();
        if (!allowed(jwt, agentId, permission, action)) {
            throw new AccessDeniedException("Permission insuffisante");
        }

        String base = properties.getApi().getBaseUrl().replaceAll("/+$", "");
        String query = request.getQueryString();
        URI target = URI.create(base + "/api/admin" + subPath + (query == null || query.isBlank() ? "" : "?" + query));
        byte[] body = request.getInputStream().readAllBytes();
        String contentType = request.getContentType();
        String relayToken = relayToken(jwt);

        ResponseEntity<byte[]> response;
        try {
            RestClient.RequestBodySpec spec = restClient.method(HttpMethod.valueOf(request.getMethod()))
                    .uri(target)
                    .headers(h -> {
                        h.set("X-MA-Relay", relayToken);
                        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                        if (contentType != null && body.length > 0) {
                            h.set(HttpHeaders.CONTENT_TYPE, contentType);
                        }
                    });
            if (body.length > 0) {
                spec = spec.body(body);
            }
            response = spec
                    .exchange((req, res) -> {
                        HttpHeaders headers = new HttpHeaders();
                        MediaType type = res.getHeaders().getContentType();
                        if (type != null) {
                            headers.setContentType(type);
                        }
                        String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                        if (disposition != null) {
                            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
                        }
                        byte[] payload = res.getBody() == null ? new byte[0] : res.getBody().readAllBytes();
                        return new ResponseEntity<>(payload, headers, res.getStatusCode());
                    });
        } catch (Exception e) {
            log.warn("Relais Colis na Nga injoignable : {} {} ({})", request.getMethod(), subPath, e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "Colis na Nga ne répond pas. Réessayez dans un instant.");
        }

        if (!"VIEW".equals(action)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("methode", request.getMethod());
            details.put("chemin", "/api/admin" + subPath);
            details.put("statut", response.getStatusCode().value());
            try {
                auditService.record(agentId, truncate("CNN_ADMIN_" + action), truncate("CNN_" + first.toUpperCase(Locale.ROOT)),
                        null, null, details);
            } catch (Exception e) {
                log.warn("Audit du relais Colis na Nga impossible : {}", e.getMessage());
            }
        }
        return response;
    }

    private volatile boolean readyValue;
    private volatile String readyReason;
    private volatile Instant readyCheckedAt = Instant.EPOCH;

    /** Sonde mise en cache : 60 s si le relais répond, 15 s sinon (pour voir vite l'activation). */
    private boolean relayReady(Jwt jwt) {
        Duration ttl = Duration.ofSeconds(readyValue ? 60 : 15);
        if (readyCheckedAt.plus(ttl).isAfter(Instant.now())) {
            return readyValue;
        }
        boolean ok;
        String reason;
        try {
            String base = properties.getApi().getBaseUrl().replaceAll("/+$", "");
            String token = relayToken(jwt);
            String[] answer = restClient.get()
                    .uri(URI.create(base + "/api/admin/dashboard"))
                    .header("X-MA-Relay", token)
                    .exchange((req, res) -> {
                        byte[] payload = res.getBody() == null ? new byte[0] : res.getBody().readAllBytes();
                        String text = new String(payload, StandardCharsets.UTF_8);
                        return new String[] {String.valueOf(res.getStatusCode().value()),
                                text.length() > 200 ? text.substring(0, 200) : text};
                    });
            int status = Integer.parseInt(answer[0]);
            ok = status >= 200 && status < 300;
            reason = ok ? null : "Colis na Nga a répondu " + status + " : " + answer[1];
        } catch (Exception e) {
            ok = false;
            reason = "Colis na Nga injoignable : " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        if (ok != readyValue || (reason != null && !reason.equals(readyReason))) {
            if (ok) {
                log.info("Relais Colis na Nga opérationnel.");
            } else {
                log.warn("Relais Colis na Nga indisponible : {}", reason);
            }
        }
        readyReason = reason;
        readyValue = ok;
        readyCheckedAt = Instant.now();
        return ok;
    }

    private boolean allowed(Jwt jwt, Long agentId, String permission, String action) {
        if (agentId == null || !JwtRoles.canSeeProject(CnnAdminScope.PROJECT)) {
            return false;
        }
        if (permissions.isDenied(agentId, permission, CnnAdminScope.PROJECT)) {
            return false;
        }
        if (JwtRoles.hasFullAccess(jwt)) {
            return true;
        }
        return permissions.hasAction(agentId, permission, action, CnnAdminScope.PROJECT);
    }

    private String relayToken(Jwt jwt) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(AUDIENCE))
                .subject(jwt.getSubject())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .id(UUID.randomUUID().toString())
                .claim("act", String.valueOf(jwt.getClaimAsString("email")))
                .claim("cnnRelay", true)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(rsaKey.getKeyID()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String truncate(String value) {
        return value.length() > 50 ? value.substring(0, 50) : value;
    }

    private static ResponseEntity<byte[]> error(HttpStatus status, String message) {
        String json = "{\"status\":" + status.value() + ",\"message\":\"" + message.replace("\"", "'") + "\"}";
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }
}
