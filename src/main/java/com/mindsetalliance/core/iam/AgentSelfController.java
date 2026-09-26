package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.auth.AuthService;
import com.mindsetalliance.core.common.security.JwtRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@Validated
public class AgentSelfController {

    private final AuthService authService;

    public AgentSelfController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.currentAgent(JwtRoles.agentId());
    }

    @PatchMapping("/me")
    public Map<String, Object> update(@RequestBody ProfileUpdateRequest request) {
        return authService.updateProfile(
                JwtRoles.agentId(),
                request.nom(),
                request.prenom(),
                request.telephone(),
                request.photo());
    }

    @PostMapping("/me/password")
    public Map<String, Object> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        return authService.changePassword(JwtRoles.agentId(), request.currentPassword(), request.newPassword());
    }

    public record ProfileUpdateRequest(String nom, String prenom, String telephone, String photo) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
}
