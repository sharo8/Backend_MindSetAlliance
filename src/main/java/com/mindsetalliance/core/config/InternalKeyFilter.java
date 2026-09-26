package com.mindsetalliance.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;
    private final boolean localProfile;

    public InternalKeyFilter(
            @Value("${MA_INTERNAL_API_KEY:}") String expectedKey,
            Environment environment) {
        this.expectedKey = expectedKey;
        this.localProfile = environment.matchesProfiles("local");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.contains("/api/auth/revocations") && !uri.startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-MA-Internal-Key");
        if (expectedKey == null || expectedKey.isBlank()) {
            if (localProfile) {
                filterChain.doFilter(request, response);
                return;
            }
            reject(response);
            return;
        }
        if (!constantTimeEquals(expectedKey, provided == null ? "" : provided)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Clé interne manquante ou invalide\"}");
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
