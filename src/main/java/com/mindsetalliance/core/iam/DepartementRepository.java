package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartementRepository extends JpaRepository<Departement, Long> {
    boolean existsByCode(String code);

    @Query("select d from Departement d where lower(d.nom) = lower(:nom)")
    Optional<Departement> findByNomIgnoreCase(@Param("nom") String nom);

    @Query("select distinct d from Departement d left join fetch d.rolesDefaut")
    List<Departement> findAllWithRoles();
}
