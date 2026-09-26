package com.mindsetalliance.core.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BulletinPaieRepository extends JpaRepository<BulletinPaie, Long> {
    Optional<BulletinPaie> findByAgentIdAndPeriodeAnneeAndPeriodeMois(Long agentId, int annee, int mois);
}
