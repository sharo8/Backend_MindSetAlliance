package com.mindsetalliance.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final int windowSeconds;

    public LoginRateLimitFilter(
            @Value("${ma.auth.login-max-attempts:5}") int maxAttempts,
            @Value("${ma.auth.login-window-seconds:60}") int windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.endsWith("/api/auth/login")
                && !path.endsWith("/api/auth/2fa/verify")
                && !path.endsWith("/api/auth/refresh")
                && !path.endsWith("/api/auth/password-setup")
                && !path.endsWith("/api/auth/password-reset");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        Window window = windows.compute(ip, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.reset.isBefore(now)) {
                return new Window(now.plusSeconds(windowSeconds));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (window.count.get() > maxAttempts) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Trop de tentatives. Réessayez plus tard.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final Instant reset;
        private final AtomicInteger count = new AtomicInteger(1);

        private Window(Instant reset) {
            this.reset = reset;
        }
    }
}
