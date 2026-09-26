package com.mindsetalliance.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {
    Optional<PasswordSetupToken> findByTokenHash(String tokenHash);

    List<PasswordSetupToken> findByAgent_IdAndUsedFalse(Long agentId);
}
