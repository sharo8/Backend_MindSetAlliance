-- Jeu de démonstration RBAC : 3 projets, 9 rôles, 10 agents
-- Mot de passe de tous les agents : ChangeMe123!
-- Secret TOTP démo (Base32) pour les rôles 2FA : JBSWY3DPEHPK3PXP

INSERT IGNORE INTO departements (code, nom) VALUES
    ('DEP-01', 'Direction générale'),
    ('DEP-02', 'Finances'),
    ('DEP-03', 'Ressources humaines'),
    ('DEP-04', 'Juridique'),
    ('DEP-05', 'Marketing'),
    ('DEP-06', 'Développement'),
    ('DEP-07', 'Support'),
    ('DEP-08', 'Commercial'),
    ('DEP-09', 'Opérations'),
    ('DEP-10', 'Communication'),
    ('DEP-11', 'Systèmes d''information');

INSERT IGNORE INTO projects (code, nom, description, statut, date_lancement, devise_gestion, backend_url) VALUES
    ('CNN', 'Colis na Nga', 'Application de livraison à Kinshasa', 'ACTIF', '2024-03-01', 'USD', 'http://ma-colisnanga:8082'),
    ('MDR', 'Mindset du Royaume', 'Chaîne éditoriale YouTube', 'ACTIF', '2023-09-15', 'USD', 'http://ma-mdr:8083'),
    ('SSS', 'Smart Sys Solution', 'Projet futur — services numériques', 'EN_PREPARATION', NULL, 'USD', NULL);

INSERT IGNORE INTO roles (nom) VALUES
    ('SUPPORT'), ('FINANCE'), ('RH'), ('JURIDIQUE'), ('MARKETING'),
    ('DEV'), ('DIRECTION'), ('COMMERCIAL'), ('ADMIN_SYSTEME');

INSERT IGNORE INTO permissions (code) VALUES
    ('VIEW_PAYROLL'), ('EDIT_INVOICE'), ('VALIDATE_COURIER'),
    ('MANAGE_AGENTS'), ('VIEW_VITRINE'), ('MANAGE_TICKETS'),
    ('VIEW_FINANCE'), ('MANAGE_HR'), ('SYSTEM_ADMIN');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.nom = 'ADMIN_SYSTEME')
   OR (r.nom = 'DIRECTION' AND p.code IN ('VIEW_PAYROLL','VIEW_FINANCE','VIEW_VITRINE','MANAGE_TICKETS','MANAGE_HR'))
   OR (r.nom = 'FINANCE' AND p.code IN ('VIEW_PAYROLL','EDIT_INVOICE','VIEW_FINANCE','VIEW_VITRINE'))
   OR (r.nom = 'RH' AND p.code IN ('VIEW_PAYROLL','MANAGE_HR','VIEW_VITRINE'))
   OR (r.nom = 'SUPPORT' AND p.code IN ('MANAGE_TICKETS','VIEW_VITRINE','VALIDATE_COURIER'))
   OR (r.nom = 'DEV' AND p.code IN ('VIEW_VITRINE','MANAGE_TICKETS'))
   OR (r.nom = 'MARKETING' AND p.code IN ('VIEW_VITRINE'))
   OR (r.nom = 'COMMERCIAL' AND p.code IN ('VIEW_VITRINE','MANAGE_TICKETS'))
   OR (r.nom = 'JURIDIQUE' AND p.code IN ('VIEW_VITRINE','MANAGE_TICKETS'));

INSERT IGNORE INTO agents (email_pro, password_hash, nom, prenom, departement_id, statut, two_factor_enabled, totp_secret) VALUES
    ('admin@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Mbala', 'Admin', (SELECT id FROM departements WHERE code='DEP-11'), 'ACTIF', true, 'JBSWY3DPEHPK3PXP'),
    ('direction@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Kabila', 'Grace', (SELECT id FROM departements WHERE code='DEP-01'), 'ACTIF', true, 'JBSWY3DPEHPK3PXP'),
    ('finance@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Mutombo', 'Joseph', (SELECT id FROM departements WHERE code='DEP-02'), 'ACTIF', true, 'JBSWY3DPEHPK3PXP'),
    ('rh@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Lukusa', 'Amina', (SELECT id FROM departements WHERE code='DEP-03'), 'ACTIF', true, 'JBSWY3DPEHPK3PXP'),
    ('support.cnn@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Nsimba', 'Patrick', (SELECT id FROM departements WHERE code='DEP-07'), 'ACTIF', false, NULL),
    ('marketing.mdr@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Tshala', 'Sarah', (SELECT id FROM departements WHERE code='DEP-05'), 'ACTIF', false, NULL),
    ('commercial.cnn@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Mbuyi', 'Claude', (SELECT id FROM departements WHERE code='DEP-08'), 'ACTIF', false, NULL),
    ('dev@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Kalonji', 'David', (SELECT id FROM departements WHERE code='DEP-06'), 'ACTIF', false, NULL),
    ('juridique@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Mwamba', 'Esther', (SELECT id FROM departements WHERE code='DEP-04'), 'ACTIF', false, NULL),
    ('patience@mindsetalliance.cd', '$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.', 'Ilunga', 'Patience', (SELECT id FROM departements WHERE code='DEP-02'), 'ACTIF', true, 'JBSWY3DPEHPK3PXP');

INSERT IGNORE INTO agent_roles (agent_id, role_id, project_id) VALUES
    ((SELECT id FROM agents WHERE email_pro='admin@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='ADMIN_SYSTEME'), NULL),
    ((SELECT id FROM agents WHERE email_pro='direction@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='DIRECTION'), NULL),
    ((SELECT id FROM agents WHERE email_pro='finance@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='FINANCE'), (SELECT id FROM projects WHERE code='CNN')),
    ((SELECT id FROM agents WHERE email_pro='rh@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='RH'), NULL),
    ((SELECT id FROM agents WHERE email_pro='support.cnn@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='SUPPORT'), (SELECT id FROM projects WHERE code='CNN')),
    ((SELECT id FROM agents WHERE email_pro='marketing.mdr@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='MARKETING'), (SELECT id FROM projects WHERE code='MDR')),
    ((SELECT id FROM agents WHERE email_pro='commercial.cnn@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='COMMERCIAL'), (SELECT id FROM projects WHERE code='CNN')),
    ((SELECT id FROM agents WHERE email_pro='dev@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='DEV'), NULL),
    ((SELECT id FROM agents WHERE email_pro='juridique@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='JURIDIQUE'), NULL),
    ((SELECT id FROM agents WHERE email_pro='patience@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='FINANCE'), (SELECT id FROM projects WHERE code='CNN')),
    ((SELECT id FROM agents WHERE email_pro='patience@mindsetalliance.cd'), (SELECT id FROM roles WHERE nom='MARKETING'), (SELECT id FROM projects WHERE code='MDR'));

INSERT IGNORE INTO taux_change (devise_source, devise_cible, taux, date_effet) VALUES
    ('USD', 'CDF', 2850.00000000, CURRENT_DATE),
    ('CDF', 'USD', 0.00035088, CURRENT_DATE);

INSERT IGNORE INTO dossiers_personnel (agent_id, numero_identite, coordonnees_bancaires, date_embauche, salaire_base, devise)
SELECT id, 'ENCRYPTED_PLACEHOLDER', 'ENCRYPTED_PLACEHOLDER', '2022-01-15', 1200.00, 'USD'
FROM agents WHERE email_pro='patience@mindsetalliance.cd';

INSERT IGNORE INTO vitrine_kpis (project_id, categorie, cle, valeur) VALUES
    ((SELECT id FROM projects WHERE code='CNN'), 'OPERATIONNEL', 'courses_livrees_jour', 0),
    ((SELECT id FROM projects WHERE code='MDR'), 'AUDIENCE', 'vues_total', 0);
