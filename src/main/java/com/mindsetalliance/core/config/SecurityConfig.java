package com.mindsetalliance.core.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, @Value("${ma.auth.issuer}") String issuer) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(issuer)
        ));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${ma.security.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> originList = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        boolean patterns = originList.stream().anyMatch(origin -> origin.contains("*"));
        if (patterns) {
            config.setAllowedOriginPatterns(originList);
        } else {
            config.setAllowedOrigins(originList);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-MA-Internal-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginRateLimitFilter loginRateLimitFilter,
                                                   InternalKeyFilter internalKeyFilter,
                                                   MustChangePasswordFilter mustChangePasswordFilter,
                                                   JwtAuthEntryPoint jwtAuthEntryPoint,
                                                   Environment environment) throws Exception {
        boolean swagger = environment.getProperty("ma.api-docs.enabled", Boolean.class, false);
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                    headers.referrerPolicy(p -> p.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    headers.permissionsPolicy(p -> p.policy("geolocation=(), microphone=(), camera=()"));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"));
                    headers.httpStrictTransportSecurity(hsts -> {
                        if (environment.getProperty("ma.security.require-https", Boolean.class, false)) {
                            hsts.includeSubDomains(true).maxAgeInSeconds(31536000);
                        } else {
                            hsts.disable();
                        }
                    });
                })
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(
                            "/api/auth/login",
                            "/api/auth/2fa/verify",
                            "/api/auth/refresh",
                            "/api/auth/password-setup",
                            "/api/auth/password-reset",
                            "/api/auth/.well-known/jwks.json",
                            // Protégé par X-MA-Internal-Key (InternalKeyFilter), pas par un jeton d'agent.
                            "/api/auth/revocations"
                    ).permitAll();
                    if (swagger) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.requestMatchers("/api/internal/**").permitAll();
                    auth.requestMatchers("/actuator/health").permitAll();
                    auth.requestMatchers("/actuator/**").denyAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .bearerTokenResolver(request -> {
                            String path = request.getRequestURI();
                            if (path.startsWith("/api/auth/login")
                                    || path.startsWith("/api/auth/refresh")
                                    || path.startsWith("/api/auth/2fa")
                                    || path.startsWith("/api/auth/password-reset")
                                    || path.startsWith("/api/auth/password-setup")
                                    || path.startsWith("/api/auth/revocations")
                                    || path.startsWith("/api/internal/")
                                    || path.contains("/.well-known/jwks.json")) {
                                return null;
                            }
                            return new DefaultBearerTokenResolver().resolve(request);
                        })
                        .authenticationEntryPoint(jwtAuthEntryPoint))
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(mustChangePasswordFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
