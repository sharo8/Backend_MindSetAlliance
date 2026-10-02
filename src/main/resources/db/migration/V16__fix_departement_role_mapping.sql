-- Les noms CDC ont remplacé les libellés courts (DEP-01…), mais le mapping V15
-- restait calé sur les anciens noms. Recalage par code = nom actuel.

DELETE FROM departement_roles;

INSERT IGNORE INTO departement_roles (departement_id, role_id)
SELECT d.id, r.id
FROM departements d
JOIN roles r ON (
    (d.code = 'DEP-01' AND r.nom = 'SUPPORT')
    OR (d.code = 'DEP-02' AND r.nom = 'COMMERCIAL')
    OR (d.code = 'DEP-03' AND r.nom = 'FINANCE')
    OR (d.code = 'DEP-04' AND r.nom = 'RH')
    OR (d.code = 'DEP-05' AND r.nom = 'JURIDIQUE')
    OR (d.code = 'DEP-06' AND r.nom = 'MARKETING')
    OR (d.code = 'DEP-07' AND r.nom = 'DEV')
    OR (d.code IN ('DEP-08') AND r.nom IN ('DIRECTION', 'CONSEIL_ADMINISTRATION'))
    OR (d.code = 'DEP-09' AND r.nom = 'SUPPORT')
    OR (d.code = 'DEP-10' AND r.nom = 'MARKETING')
    OR (d.code = 'DEP-11' AND r.nom = 'DEV')
);
