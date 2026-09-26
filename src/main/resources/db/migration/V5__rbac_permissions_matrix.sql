-- Matrice RBAC : permissions manquantes + métadonnées module/libellé (sans toucher V1–V4)

ALTER TABLE permissions
    ADD COLUMN module VARCHAR(40) NULL,
    ADD COLUMN libelle VARCHAR(120) NULL;

INSERT IGNORE INTO permissions (code) VALUES
    ('MANAGE_USERS'),
    ('MANAGE_PERMISSIONS'),
    ('VIEW_MAP'),
    ('VALIDATE_DRIVER'),
    ('VIEW_BI_DASHBOARD'),
    ('VIEW_MEDIA'),
    ('MANAGE_PARTNERS'),
    ('VIEW_MONITORING');

UPDATE permissions SET module = 'RH', libelle = 'Consulter la paie' WHERE code = 'VIEW_PAYROLL';
UPDATE permissions SET module = 'Finance', libelle = 'Modifier une facture' WHERE code = 'EDIT_INVOICE';
UPDATE permissions SET module = 'Commercial', libelle = 'Valider un coursier' WHERE code = 'VALIDATE_COURIER';
UPDATE permissions SET module = 'Technique', libelle = 'Administrer les agents (historique)' WHERE code = 'MANAGE_AGENTS';
UPDATE permissions SET module = 'Direction', libelle = 'Consulter la vitrine / KPIs' WHERE code = 'VIEW_VITRINE';
UPDATE permissions SET module = 'Technique', libelle = 'Gérer les tickets' WHERE code = 'MANAGE_TICKETS';
UPDATE permissions SET module = 'Finance', libelle = 'Consulter la finance' WHERE code = 'VIEW_FINANCE';
UPDATE permissions SET module = 'RH', libelle = 'Gérer les dossiers RH' WHERE code = 'MANAGE_HR';
UPDATE permissions SET module = 'Technique', libelle = 'Administration système' WHERE code = 'SYSTEM_ADMIN';
UPDATE permissions SET module = 'RH', libelle = 'Gérer les utilisateurs' WHERE code = 'MANAGE_USERS';
UPDATE permissions SET module = 'Technique', libelle = 'Gérer la matrice des permissions' WHERE code = 'MANAGE_PERMISSIONS';
UPDATE permissions SET module = 'Opérations', libelle = 'Cartographie temps réel' WHERE code = 'VIEW_MAP';
UPDATE permissions SET module = 'Commercial', libelle = 'Valider un chauffeur / coursier' WHERE code = 'VALIDATE_DRIVER';
UPDATE permissions SET module = 'Direction', libelle = 'Tableau de bord BI' WHERE code = 'VIEW_BI_DASHBOARD';
UPDATE permissions SET module = 'Marketing', libelle = 'Médiathèque / analytics' WHERE code = 'VIEW_MEDIA';
UPDATE permissions SET module = 'Juridique', libelle = 'Gérer les partenaires' WHERE code = 'MANAGE_PARTNERS';
UPDATE permissions SET module = 'Technique', libelle = 'Monitoring technique' WHERE code = 'VIEW_MONITORING';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'RH' AND p.code = 'MANAGE_USERS';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'ADMIN_SYSTEME' AND p.code IN (
    'MANAGE_USERS', 'MANAGE_PERMISSIONS', 'VIEW_MAP', 'VALIDATE_DRIVER',
    'VIEW_BI_DASHBOARD', 'VIEW_MEDIA', 'MANAGE_PARTNERS', 'VIEW_MONITORING'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'DIRECTION' AND p.code IN ('VIEW_BI_DASHBOARD', 'VIEW_MAP');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'SUPPORT' AND p.code IN ('VIEW_MAP', 'VALIDATE_DRIVER');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'COMMERCIAL' AND p.code IN ('VALIDATE_DRIVER');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'MARKETING' AND p.code IN ('VIEW_MEDIA');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'JURIDIQUE' AND p.code IN ('MANAGE_PARTNERS');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'DEV' AND p.code IN ('VIEW_MONITORING');
