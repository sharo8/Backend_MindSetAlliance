package com.mindsetalliance.core.vitrine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VitrineKpiRepository extends JpaRepository<VitrineKpi, Long> {
    Optional<VitrineKpi> findByProjectIdAndCategorieAndCle(Long projectId, String categorie, String cle);
    List<VitrineKpi> findByProject_CodeIn(List<String> codes);
    List<VitrineKpi> findAll();
}
