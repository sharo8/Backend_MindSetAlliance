package com.mindsetalliance.core.auth;

import com.mindsetalliance.core.common.security.JwtRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/2fa/verify")
    public Map<String, Object> verify2fa(@RequestBody @Valid TwoFactorRequest request) {
        return authService.verify2fa(request.challengeId(), request.code());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody @Validated RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody(required = false) RefreshRequest request) {
        String refresh = request == null ? null : request.refreshToken();
        authService.logout(JwtRoles.agentId(), refresh);
        return Map.of("message", "Session invalidée");
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.currentAgent(JwtRoles.agentId());
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        return authService.changePassword(JwtRoles.agentId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/password-setup")
    public Map<String, Object> passwordSetup(@RequestBody @Valid PasswordSetupRequest request) {
        return authService.completePasswordSetup(request.token(), request.newPassword());
    }

    @PostMapping("/password-reset")
    public Map<String, String> passwordReset(@RequestBody @Valid PasswordResetRequest request) {
        return authService.requestPasswordReset(request.email());
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return authService.jwks();
    }

    @GetMapping("/revocations")
    public Map<String, Object> revocations() {
        return Map.of("agentIds", authService.revokedAgentIds());
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record TwoFactorRequest(@NotBlank String challengeId, @NotBlank String code) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record PasswordSetupRequest(@NotBlank String token, @NotBlank String newPassword) {}
    public record PasswordResetRequest(@Email @NotBlank String email) {}
}
