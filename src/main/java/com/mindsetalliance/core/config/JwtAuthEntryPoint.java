package com.mindsetalliance.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Réponse 401 JSON sans exposer le jeton.
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthEntryPoint.class);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String header = request.getHeader("Authorization");
        String reason = classify(header, authException);
        log.debug("JWT 401 {} {} : {}", request.getMethod(), request.getRequestURI(), reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"horodatage\":\"" + Instant.now()
                + "\",\"statut\":401,\"message\":\"Jeton d’authentification manquant ou invalide.\"}");
    }

    private String classify(String header, AuthenticationException authException) {
        if (header == null || header.isBlank()) {
            return "ABSENT";
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "MALFORME";
        }
        Throwable current = authException;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            String msg = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (current instanceof JwtValidationException || msg.contains("expired")) {
                return "EXPIRE";
            }
            if (current instanceof InvalidBearerTokenException || current instanceof JwtException) {
                if (msg.contains("signature") || msg.contains("signed")) {
                    return "SIGNATURE";
                }
                if (msg.contains("expired")) {
                    return "EXPIRE";
                }
                if (msg.contains("malformed") || msg.contains("invalid")) {
                    return "MALFORME";
                }
                return "JWT:" + name;
            }
            current = current.getCause();
        }
        return "REJET:" + authException.getClass().getSimpleName();
    }
}
