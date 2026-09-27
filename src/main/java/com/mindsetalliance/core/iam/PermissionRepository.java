package com.mindsetalliance.core.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);

    @Query(value = """
            SELECT p.code FROM role_permissions rp
            INNER JOIN permissions p ON p.id = rp.permission_id
            WHERE rp.role_id = :roleId
            ORDER BY p.code
            """, nativeQuery = true)
    List<String> findCodesByRoleId(@Param("roleId") Long roleId);

    @Query(value = """
            SELECT p.code, p.libelle, COUNT(rp.role_id) AS n
            FROM permissions p
            LEFT JOIN role_permissions rp ON rp.permission_id = p.id
            GROUP BY p.id, p.code, p.libelle
            ORDER BY n DESC, p.code ASC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> countRolesPerPermissionTop10();
}
