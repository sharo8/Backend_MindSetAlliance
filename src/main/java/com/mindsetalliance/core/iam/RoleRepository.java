package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByNom(String nom);

    @Query("select count(r) from Role r join r.permissions p where r.nom in :noms and p.code = :code")
    long countHavingPermission(@Param("noms") Collection<String> noms, @Param("code") String code);
}
