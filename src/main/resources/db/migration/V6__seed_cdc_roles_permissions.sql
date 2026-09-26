-- V6 — catalogue CDC (8 départements) + permissions par rôle + agents de démo
-- Idempotent : INSERT IGNORE / UPDATE ciblés, sans dupliquer V1–V5.

-- Noms CDC pour DEP-01 … DEP-08 (codes déjà présents)
UPDATE departements SET nom = 'Service Clientèle, Support et Opérations' WHERE code = 'DEP-01';
UPDATE departements SET nom = 'Commercial et Partenariats' WHERE code = 'DEP-02';
UPDATE departements SET nom = 'Service Financier' WHERE code = 'DEP-03';
UPDATE departements SET nom = 'Ressources Humaines' WHERE code = 'DEP-04';
UPDATE departements SET nom = 'Juridique et Conformité' WHERE code = 'DEP-05';
UPDATE departements SET nom = 'Marketing, Média et Créateurs de Contenu' WHERE code = 'DEP-06';
UPDATE departements SET nom = 'Développeurs et Technical Ops' WHERE code = 'DEP-07';
UPDATE departements SET nom = 'Direction Générale et Conseil d''Administration' WHERE code = 'DEP-08';

INSERT IGNORE INTO roles (nom) VALUES ('CONSEIL_ADMINISTRATION');

INSERT IGNORE INTO permissions (code) VALUES
    ('VIEW_FLEET_MAP'),
    ('MANAGE_COURSES'),
    ('RESOLVE_INCIDENTS'),
    ('MANAGE_ENTERPRISE_ACCOUNTS'),
    ('MANAGE_SUBSCRIPTIONS'),
    ('MANAGE_INVOICING'),
    ('VIEW_B2C_VOLUME'),
    ('MANAGE_INTERNAL_AGENTS'),
    ('MANAGE_STAFF'),
    ('MANAGE_ACCESS'),
    ('MANAGE_PARTNER_REGISTRY'),
    ('MANAGE_COMPLIANCE'),
    ('VIEW_MARKETING_PERFORMANCE'),
    ('MANAGE_MEDIA_LIBRARY'),
    ('MANAGE_BUG_TICKETS'),
    ('VIEW_EXECUTIVE_DASHBOARD'),
    ('EXPORT_FINANCIAL_REPORTS'),
    ('VIEW_AUDIT'),
    ('VIEW_TICKETS'),
    ('MANAGE_OWN_PROFILE');

UPDATE permissions SET module = 'Opérations', libelle = 'Cartographie flotte' WHERE code = 'VIEW_FLEET_MAP';
UPDATE permissions SET module = 'Opérations', libelle = 'Gérer les courses' WHERE code = 'MANAGE_COURSES';
UPDATE permissions SET module = 'Opérations', libelle = 'Résoudre les incidents' WHERE code = 'RESOLVE_INCIDENTS';
UPDATE permissions SET module = 'Commercial', libelle = 'Gérer les comptes entreprises' WHERE code = 'MANAGE_ENTERPRISE_ACCOUNTS';
UPDATE permissions SET module = 'Finance', libelle = 'Gérer les abonnements' WHERE code = 'MANAGE_SUBSCRIPTIONS';
UPDATE permissions SET module = 'Finance', libelle = 'Gérer la facturation' WHERE code = 'MANAGE_INVOICING';
UPDATE permissions SET module = 'Finance', libelle = 'Consulter le volume B2C' WHERE code = 'VIEW_B2C_VOLUME';
UPDATE permissions SET module = 'RH', libelle = 'Gérer les agents internes' WHERE code = 'MANAGE_INTERNAL_AGENTS';
UPDATE permissions SET module = 'RH', libelle = 'Gérer le personnel' WHERE code = 'MANAGE_STAFF';
UPDATE permissions SET module = 'RH', libelle = 'Administrer les accès (rôles)' WHERE code = 'MANAGE_ACCESS';
UPDATE permissions SET module = 'Juridique', libelle = 'Registre des partenaires' WHERE code = 'MANAGE_PARTNER_REGISTRY';
UPDATE permissions SET module = 'Juridique', libelle = 'Conformité' WHERE code = 'MANAGE_COMPLIANCE';
UPDATE permissions SET module = 'Marketing', libelle = 'Performance marketing' WHERE code = 'VIEW_MARKETING_PERFORMANCE';
UPDATE permissions SET module = 'Marketing', libelle = 'Médiathèque' WHERE code = 'MANAGE_MEDIA_LIBRARY';
UPDATE permissions SET module = 'Technique', libelle = 'Tickets bugs / incidents tech' WHERE code = 'MANAGE_BUG_TICKETS';
UPDATE permissions SET module = 'Direction', libelle = 'Tableau de bord exécutif' WHERE code = 'VIEW_EXECUTIVE_DASHBOARD';
UPDATE permissions SET module = 'Direction', libelle = 'Exporter les rapports financiers' WHERE code = 'EXPORT_FINANCIAL_REPORTS';
UPDATE permissions SET module = 'Direction', libelle = 'Consulter l''audit' WHERE code = 'VIEW_AUDIT';
UPDATE permissions SET module = 'Transversal', libelle = 'Consulter les tickets' WHERE code = 'VIEW_TICKETS';
UPDATE permissions SET module = 'Transversal', libelle = 'Gérer son propre profil' WHERE code = 'MANAGE_OWN_PROFILE';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'SUPPORT' AND p.code IN (
    'VIEW_FLEET_MAP','MANAGE_COURSES','RESOLVE_INCIDENTS','VIEW_TICKETS','MANAGE_OWN_PROFILE','MANAGE_TICKETS'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'COMMERCIAL' AND p.code IN (
    'VALIDATE_COURIER','MANAGE_ENTERPRISE_ACCOUNTS','VIEW_TICKETS','MANAGE_OWN_PROFILE','MANAGE_TICKETS'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'FINANCE' AND p.code IN (
    'MANAGE_SUBSCRIPTIONS','MANAGE_INVOICING','VIEW_B2C_VOLUME','VIEW_PAYROLL','VIEW_TICKETS','MANAGE_OWN_PROFILE',
    'VIEW_FINANCE','MANAGE_TICKETS'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'RH' AND p.code IN (
    'MANAGE_INTERNAL_AGENTS','MANAGE_STAFF','MANAGE_ACCESS','VIEW_PAYROLL','VIEW_TICKETS','MANAGE_OWN_PROFILE',
    'MANAGE_USERS','MANAGE_HR','MANAGE_TICKETS'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'JURIDIQUE' AND p.code IN (
    'MANAGE_PARTNER_REGISTRY','MANAGE_COMPLIANCE','VIEW_TICKETS','MANAGE_OWN_PROFILE','MANAGE_TICKETS','MANAGE_PARTNERS'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'MARKETING' AND p.code IN (
    'VIEW_MARKETING_PERFORMANCE','MANAGE_MEDIA_LIBRARY','VIEW_TICKETS','MANAGE_OWN_PROFILE','VIEW_MEDIA'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'DEV' AND p.code IN (
    'VIEW_MONITORING','MANAGE_BUG_TICKETS','VIEW_TICKETS','MANAGE_OWN_PROFILE','MANAGE_TICKETS'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'DIRECTION' AND p.code IN (
    'VIEW_EXECUTIVE_DASHBOARD','EXPORT_FINANCIAL_REPORTS','VIEW_AUDIT','VIEW_PAYROLL','VIEW_B2C_VOLUME',
    'VIEW_TICKETS','MANAGE_OWN_PROFILE','VIEW_FINANCE','MANAGE_TICKETS','VIEW_BI_DASHBOARD'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'CONSEIL_ADMINISTRATION' AND p.code IN (
    'VIEW_EXECUTIVE_DASHBOARD','MANAGE_OWN_PROFILE','VIEW_BI_DASHBOARD'
);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom = 'ADMIN_SYSTEME' AND p.code IN (
    'MANAGE_ACCESS','VIEW_MONITORING','VIEW_AUDIT','VIEW_TICKETS','MANAGE_OWN_PROFILE',
    'MANAGE_USERS','MANAGE_PERMISSIONS','MANAGE_TICKETS'
);

-- Mot de passe ChangeMe123! (même hash que V2)
INSERT IGNORE INTO agents (email_pro, password_hash, nom, prenom, departement_id, statut, two_factor_enabled) VALUES
    ('commercial.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Commercial', (SELECT id FROM departements WHERE code='DEP-02'), 'ACTIF', 0),
    ('finance.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Finance', (SELECT id FROM departements WHERE code='DEP-03'), 'ACTIF', 0),
    ('rh.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'RH', (SELECT id FROM departements WHERE code='DEP-04'), 'ACTIF', 0),
    ('juridique.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Juridique', (SELECT id FROM departements WHERE code='DEP-05'), 'ACTIF', 0),
    ('marketing.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Marketing', (SELECT id FROM departements WHERE code='DEP-06'), 'ACTIF', 0),
    ('dev.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Dev', (SELECT id FROM departements WHERE code='DEP-07'), 'ACTIF', 0),
    ('direction.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Direction', (SELECT id FROM departements WHERE code='DEP-08'), 'ACTIF', 0),
    ('admin.demo@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Demo', 'Admin', (SELECT id FROM departements WHERE code='DEP-07'), 'ACTIF', 0);

INSERT IGNORE INTO agent_roles (agent_id, role_id, project_id) VALUES
    ((SELECT id FROM agents WHERE email_pro='commercial.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='COMMERCIAL'), (SELECT id FROM projects WHERE code='CNN')),
    ((SELECT id FROM agents WHERE email_pro='finance.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='FINANCE'), (SELECT id FROM projects WHERE code='CNN')),
    ((SELECT id FROM agents WHERE email_pro='rh.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='RH'), NULL),
    ((SELECT id FROM agents WHERE email_pro='juridique.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='JURIDIQUE'), NULL),
    ((SELECT id FROM agents WHERE email_pro='marketing.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='MARKETING'), (SELECT id FROM projects WHERE code='MDR')),
    ((SELECT id FROM agents WHERE email_pro='dev.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='DEV'), NULL),
    ((SELECT id FROM agents WHERE email_pro='direction.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='DIRECTION'), NULL),
    ((SELECT id FROM agents WHERE email_pro='admin.demo@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='ADMIN_SYSTEME'), NULL);
