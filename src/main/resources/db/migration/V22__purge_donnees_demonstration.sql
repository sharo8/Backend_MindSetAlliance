-- V22 : plus aucune donnée fictive dans MA Workspace.
-- Supprime les comptes de démonstration (V2, V6), tout ce qui leur est rattaché,
-- les tickets générés (CNN-D…, CNN-T…), les jeux ops_records factices restants,
-- le taux de change fixe, l'indicateur vitrine MDR à 0 et le projet Smart Sys Solution
-- (qui n'est pas un produit Mindset Alliance).
-- Les comptes réels et les tickets réels ne sont pas touchés.

DROP TABLE IF EXISTS v22_demo_agents;
CREATE TABLE v22_demo_agents (id BIGINT PRIMARY KEY);
INSERT INTO v22_demo_agents (id)
SELECT id FROM agents WHERE email_pro IN (
    'admin@mindsetalliance.cd', 'direction@mindsetalliance.cd', 'finance@mindsetalliance.cd',
    'rh@mindsetalliance.cd', 'support.cnn@mindsetalliance.cd', 'marketing.mdr@mindsetalliance.cd',
    'commercial.cnn@mindsetalliance.cd', 'dev@mindsetalliance.cd', 'juridique@mindsetalliance.cd',
    'patience@mindsetalliance.cd',
    'commercial.demo@mindsetalliance.cd', 'finance.demo@mindsetalliance.cd', 'rh.demo@mindsetalliance.cd',
    'juridique.demo@mindsetalliance.cd', 'marketing.demo@mindsetalliance.cd', 'dev.demo@mindsetalliance.cd',
    'direction.demo@mindsetalliance.cd', 'admin.demo@mindsetalliance.cd');

-- Tickets à supprimer : générés par les migrations, ouverts par un compte démo, ou du projet SSS.
DROP TABLE IF EXISTS v22_demo_tickets;
CREATE TABLE v22_demo_tickets (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO v22_demo_tickets (id)
SELECT t.id FROM tickets t
WHERE t.reference LIKE 'CNN-D%'
   OR t.reference LIKE 'CNN-T%'
   OR t.demandeur_id IN (SELECT id FROM v22_demo_agents)
   OR t.project_id IN (SELECT id FROM projects WHERE code = 'SSS');

DELETE FROM ticket_commentaires WHERE ticket_id IN (SELECT id FROM v22_demo_tickets);
DELETE FROM ticket_pieces_jointes WHERE ticket_id IN (SELECT id FROM v22_demo_tickets);
DELETE FROM ticket_reattributions WHERE ticket_id IN (SELECT id FROM v22_demo_tickets);
DELETE FROM ticket_statut_historique WHERE ticket_id IN (SELECT id FROM v22_demo_tickets);
DELETE FROM tickets WHERE id IN (SELECT id FROM v22_demo_tickets);

-- Traces des comptes démo sur des tickets réels.
UPDATE tickets SET attributaire_id = NULL WHERE attributaire_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM ticket_commentaires WHERE auteur_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM ticket_reattributions
WHERE ancien_agent_id IN (SELECT id FROM v22_demo_agents)
   OR nouvel_agent_id IN (SELECT id FROM v22_demo_agents)
   OR auteur_id IN (SELECT id FROM v22_demo_agents);
UPDATE ticket_statut_historique SET agent_id = NULL WHERE agent_id IN (SELECT id FROM v22_demo_agents);

-- Données rattachées aux comptes démo.
DELETE FROM agent_permission_overrides
WHERE agent_id IN (SELECT id FROM v22_demo_agents) OR accorde_par IN (SELECT id FROM v22_demo_agents);
DELETE FROM agent_permission_actions WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM assistant_conversations WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM agent_roles WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM refresh_tokens WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM auth_challenges WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM password_setup_tokens WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM dossiers_personnel WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM presences WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM conges WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM elements_paie WHERE bulletin_id IN (SELECT id FROM bulletins_paie WHERE agent_id IN (SELECT id FROM v22_demo_agents));
DELETE FROM bulletins_paie WHERE agent_id IN (SELECT id FROM v22_demo_agents);
DELETE FROM notifications WHERE agent_id IN (SELECT id FROM v22_demo_agents);
UPDATE audit_log SET agent_id = NULL WHERE agent_id IN (SELECT id FROM v22_demo_agents);
UPDATE ecritures_financieres SET created_by = NULL WHERE created_by IN (SELECT id FROM v22_demo_agents);
UPDATE documents SET created_by = NULL WHERE created_by IN (SELECT id FROM v22_demo_agents);
UPDATE ops_records SET created_by = NULL WHERE created_by IN (SELECT id FROM v22_demo_agents);
DELETE FROM agents WHERE id IN (SELECT id FROM v22_demo_agents);

-- Jeux factices restants de V7 (hors Colis na Nga, déjà retirés par V20).
DELETE FROM ops_records
WHERE (module = 'LEAVE' AND titre = 'Congé annuel — Demo RH')
   OR (module = 'PARTNER' AND titre = 'Association Kin Logistics')
   OR (module = 'MEDIA' AND titre = 'Visuel campagne Pâques')
   OR (module = 'BUG' AND titre = 'Latence API tickets');

-- Taux de change fixe de démonstration.
DELETE FROM taux_change
WHERE (devise_source = 'USD' AND devise_cible = 'CDF' AND taux = 2850.00000000)
   OR (devise_source = 'CDF' AND devise_cible = 'USD' AND taux = 0.00035088);

-- Indicateur vitrine MDR posé à 0 sans source réelle.
DELETE k FROM vitrine_kpis k
INNER JOIN projects p ON p.id = k.project_id AND p.code = 'MDR'
WHERE k.cle = 'vues_total' AND k.valeur = 0;

-- Projet Smart Sys Solution.
DELETE FROM agent_permission_overrides WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM agent_permission_actions WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM agent_roles WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM ticket_sequences WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM ops_records WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM vitrine_kpis WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM budgets WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM factures WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM ecritures_financieres WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM documents WHERE project_id IN (SELECT id FROM projects WHERE code = 'SSS');
DELETE FROM projects WHERE code = 'SSS';

DROP TABLE IF EXISTS v22_demo_tickets;
DROP TABLE IF EXISTS v22_demo_agents;
