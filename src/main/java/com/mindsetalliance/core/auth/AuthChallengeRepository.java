package com.mindsetalliance.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, Long> {
    Optional<AuthChallenge> findByChallengeId(String challengeId);
}
