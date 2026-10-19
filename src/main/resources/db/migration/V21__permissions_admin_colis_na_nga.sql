-- V21 — Administration Colis na Nga depuis MA Workspace (relais vers l'API admin Colis na Nga).
-- Une permission par domaine ; les actions VIEW / CREATE / UPDATE / DELETE se règlent par agent.
-- ADMIN_SYSTEME et DIRECTION ont déjà l'accès total ; les autres rôles se règlent dans la matrice.

INSERT IGNORE INTO permissions (code) VALUES
    ('VIEW_CNN_DASHBOARD'),
    ('MANAGE_CNN_ORDERS'),
    ('MANAGE_CNN_AGENTS'),
    ('MANAGE_CNN_MOTARDS'),
    ('MANAGE_CNN_ASSOCIATIONS'),
    ('MANAGE_CNN_CLIENTS'),
    ('MANAGE_CNN_SUBSCRIPTIONS'),
    ('MANAGE_CNN_PAYMENTS'),
    ('MANAGE_CNN_SETTINGS'),
    ('MANAGE_CNN_ADMINS');

UPDATE permissions SET module = 'Colis na Nga', libelle = 'Tableau de bord Colis na Nga', decomposable = 0 WHERE code = 'VIEW_CNN_DASHBOARD';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Commandes et livraisons', decomposable = 1 WHERE code = 'MANAGE_CNN_ORDERS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Livreurs', decomposable = 1 WHERE code = 'MANAGE_CNN_AGENTS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Motards partenaires et signalements', decomposable = 1 WHERE code = 'MANAGE_CNN_MOTARDS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Associations, gains et reversements', decomposable = 1 WHERE code = 'MANAGE_CNN_ASSOCIATIONS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Clients et comptes', decomposable = 1 WHERE code = 'MANAGE_CNN_CLIENTS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Abonnements et factures', decomposable = 1 WHERE code = 'MANAGE_CNN_SUBSCRIPTIONS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Paiements Mobile Money', decomposable = 1 WHERE code = 'MANAGE_CNN_PAYMENTS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Tarifs et taux de change', decomposable = 1 WHERE code = 'MANAGE_CNN_SETTINGS';
UPDATE permissions SET module = 'Colis na Nga', libelle = 'Administrateurs et audit de l''application', decomposable = 1 WHERE code = 'MANAGE_CNN_ADMINS';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.nom IN ('ADMIN_SYSTEME', 'DIRECTION') AND p.module = 'Colis na Nga';
