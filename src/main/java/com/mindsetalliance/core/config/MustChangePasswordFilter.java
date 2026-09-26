package com.mindsetalliance.core.config;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.AgentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final AgentRepository agentRepository;

    public MustChangePasswordFilter(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        Long agentId;
        try {
            agentId = JwtRoles.agentId();
        } catch (Exception ex) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean mustChange = agentRepository.findById(agentId)
                .map(agent -> agent.isDoitChangerMotDePasse())
                .orElse(false);
        if (!mustChange || allowed(request.getMethod(), request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(403);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"message\":\"Vous devez changer votre mot de passe avant d’accéder à l’espace de travail.\"}");
    }

    private boolean allowed(String method, String path) {
        String m = method == null ? "" : method.toUpperCase();
        if ("GET".equals(m) && (path.endsWith("/api/auth/me") || path.endsWith("/api/agents/me"))) {
            return true;
        }
        if ("POST".equals(m) && (path.endsWith("/api/auth/password")
                || path.endsWith("/api/agents/me/password")
                || path.endsWith("/api/auth/logout")
                || path.endsWith("/api/auth/refresh"))) {
            return true;
        }
        return "GET".equals(m) && path.endsWith("/actuator/health");
    }
}
