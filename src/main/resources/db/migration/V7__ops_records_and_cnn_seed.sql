-- Opérations métier (CRUD métier, pas de suppression physique)
CREATE TABLE IF NOT EXISTS ops_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(40) NOT NULL,
    project_id BIGINT NULL,
    titre VARCHAR(255) NOT NULL,
    description TEXT NULL,
    statut VARCHAR(40) NOT NULL DEFAULT 'ACTIF',
    priorite VARCHAR(20) NULL,
    zone VARCHAR(80) NULL,
    canal VARCHAR(40) NULL,
    montant DECIMAL(14,2) NULL,
    meta_json LONGTEXT NULL,
    motif VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ops_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_ops_agent FOREIGN KEY (created_by) REFERENCES agents(id),
    INDEX idx_ops_module (module),
    INDEX idx_ops_statut (statut),
    INDEX idx_ops_created (created_at)
);

ALTER TABLE documents
    ADD COLUMN statut VARCHAR(40) NOT NULL DEFAULT 'ACTIF',
    ADD COLUMN motif_archivage VARCHAR(500) NULL;

-- Courses CNN 30 jours (zones Kinshasa)
INSERT INTO ops_records (module, project_id, titre, statut, zone, created_at)
SELECT 'COURSE', p.id,
       CONCAT('Course ', n.n),
       ELT(1 + MOD(n.n, 4), 'LIVREE', 'EN_COURS', 'ANNULEE', 'INCIDENT'),
       ELT(1 + MOD(n.n, 5), 'Gombe', 'Limete', 'Ngaliema', 'Kintambo', 'Bandalungwa'),
       DATE_SUB(NOW(), INTERVAL MOD(n.n, 30) DAY)
FROM projects p
JOIN (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8
    UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15
    UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION SELECT 21 UNION SELECT 22
    UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29
    UNION SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION SELECT 36
    UNION SELECT 37 UNION SELECT 38 UNION SELECT 39 UNION SELECT 40 UNION SELECT 41 UNION SELECT 42 UNION SELECT 43
    UNION SELECT 44 UNION SELECT 45 UNION SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49 UNION SELECT 50
    UNION SELECT 51 UNION SELECT 52 UNION SELECT 53 UNION SELECT 54 UNION SELECT 55 UNION SELECT 56 UNION SELECT 57
    UNION SELECT 58 UNION SELECT 59 UNION SELECT 60
) n
WHERE p.code = 'CNN';

INSERT INTO ops_records (module, project_id, titre, description, statut, priorite, created_at)
SELECT 'INCIDENT', p.id, CONCAT('Incident flotte #', n.n), 'Retard ou colis non scanné.',
       ELT(1 + MOD(n.n, 3), 'OUVERT', 'EN_COURS', 'RESOLU'),
       ELT(1 + MOD(n.n, 4), 'CRITIQUE', 'ELEVEE', 'NORMALE', 'FAIBLE'),
       DATE_SUB(NOW(), INTERVAL n.n DAY)
FROM projects p
JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8) n
WHERE p.code = 'CNN';

INSERT INTO ops_records (module, project_id, titre, statut, created_at)
SELECT 'COURIER', id, 'Koffi Mpiana', 'EN_VALIDATION', NOW() FROM projects WHERE code='CNN';
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
SELECT 'COURIER', id, 'Grace Ilunga', 'VALIDE', NOW() FROM projects WHERE code='CNN';
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
SELECT 'ENTERPRISE', id, 'Rawbank — contrat logistique', 'ACTIF', NOW() FROM projects WHERE code='CNN';
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
SELECT 'SUBSCRIPTION', id, 'Abonnement CNN Pro', 'ACTIF', NOW() FROM projects WHERE code='CNN';
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
SELECT 'INVOICE', id, 'Facture F-2026-091', 'EN_RETARD', NOW() FROM projects WHERE code='CNN';
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
VALUES ('LEAVE', NULL, 'Congé annuel — Demo RH', 'EN_ATTENTE', NOW());
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
VALUES ('PARTNER', NULL, 'Association Kin Logistics', 'ACTIF', NOW());
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
SELECT 'MEDIA', id, 'Visuel campagne Pâques', 'ACTIF', NOW() FROM projects WHERE code='MDR';
INSERT INTO ops_records (module, project_id, titre, statut, created_at)
VALUES ('BUG', NULL, 'Latence API tickets', 'OUVERT', NOW());

INSERT INTO ops_records (module, project_id, titre, canal, montant, statut, created_at)
SELECT 'INVOICE', p.id, CONCAT('Encaissement ', ELT(1+MOD(n.n,4),'M-Pesa','Orange Money','Airtel Money','Virement')),
       ELT(1+MOD(n.n,4),'MPESA','ORANGE','AIRTEL','VIREMENT'),
       120 + n.n * 17,
       'VALIDEE',
       DATE_SUB(NOW(), INTERVAL n.n MONTH)
FROM projects p
JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE p.code = 'CNN';
