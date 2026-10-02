package com.mindsetalliance.core.integration.cnn;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Émet les jetons courts (60 s par défaut) que le Core présente à Colis na Nga.
 *
 * Différences avec le jeton de session d'un agent :
 * <ul>
 *   <li>{@code aud = colis-na-nga} : Colis na Nga refuse tout jeton sans cette audience,
 *       donc un jeton de session volé dans un navigateur ne lui ouvre rien ;</li>
 *   <li>seuls les rôles utiles à CNN (projet CNN ou rôle transverse) sont transmis ;</li>
 *   <li>durée de vie très courte et {@code jti} unique, pour la traçabilité côté CNN.</li>
 * </ul>
 */
@Component
public class CnnServiceTokenIssuer {

    public static final String PROJECT_CODE = "CNN";
    public static final String SYSTEM_SUBJECT = "service-ma-core";

    private final JwtEncoder jwtEncoder;
    private final RSAKey rsaKey;
    private final CnnIntegrationProperties properties;
    private final String issuer;

    public CnnServiceTokenIssuer(JwtEncoder jwtEncoder, RSAKey rsaKey, CnnIntegrationProperties properties,
                                 @Value("${ma.auth.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.rsaKey = rsaKey;
        this.properties = properties;
        this.issuer = issuer;
    }

    /** Jeton relayant l'identité et les rôles CNN de l'agent connecté. */
    public String forAgent(Jwt agentJwt) {
        List<Map<String, Object>> roles = JwtRoles.roleClaims(agentJwt).stream()
                .filter(role -> role.get("projectCode") == null
                        || PROJECT_CODE.equalsIgnoreCase(String.valueOf(role.get("projectCode"))))
                .toList();
        Object email = agentJwt.getClaim("email");
        return sign(agentJwt.getSubject(), email == null ? null : String.valueOf(email), roles);
    }

    /** Jeton technique du Core lui-même (rattrapage nocturne). */
    public String forSystem() {
        return sign(SYSTEM_SUBJECT, null, List.of(Map.of("projectCode", PROJECT_CODE, "role", "SYSTEME")));
    }

    private String sign(String subject, String email, List<Map<String, Object>> roles) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(properties.getAudience()))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.getServiceTokenSeconds()))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles);
        if (email != null) {
            claims.claim("email", email);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(rsaKey.getKeyID()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
