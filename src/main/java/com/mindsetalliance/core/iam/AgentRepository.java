package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long>, JpaSpecificationExecutor<Agent> {
    Optional<Agent> findByEmailProIgnoreCase(String emailPro);

    long countByDepartement_Id(Long departementId);

    @Query("select distinct a from Agent a left join fetch a.departement")
    List<Agent> findAllWithDepartement();
}
