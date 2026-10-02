ALTER TABLE departements
    ADD COLUMN description VARCHAR(500) NULL,
    ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF';

CREATE TABLE departement_roles (
    departement_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (departement_id, role_id),
    CONSTRAINT fk_dep_roles_dep FOREIGN KEY (departement_id) REFERENCES departements (id),
    CONSTRAINT fk_dep_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

INSERT IGNORE INTO departement_roles (departement_id, role_id)
SELECT d.id, r.id
FROM departements d
JOIN roles r ON (
    (d.code = 'DEP-01' AND r.nom IN ('DIRECTION', 'CONSEIL_ADMINISTRATION'))
    OR (d.code = 'DEP-02' AND r.nom = 'FINANCE')
    OR (d.code = 'DEP-03' AND r.nom = 'RH')
    OR (d.code = 'DEP-04' AND r.nom = 'JURIDIQUE')
    OR (d.code = 'DEP-05' AND r.nom = 'MARKETING')
    OR (d.code = 'DEP-06' AND r.nom = 'DEV')
    OR (d.code = 'DEP-07' AND r.nom = 'SUPPORT')
    OR (d.code = 'DEP-08' AND r.nom = 'COMMERCIAL')
    OR (d.code = 'DEP-09' AND r.nom = 'SUPPORT')
    OR (d.code = 'DEP-10' AND r.nom = 'MARKETING')
    OR (d.code = 'DEP-11' AND r.nom = 'DEV')
);
