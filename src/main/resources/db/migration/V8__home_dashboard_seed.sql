-- Jeu de tickets CNN (7–30 jours) pour le dashboard d'accueil
INSERT IGNORE INTO tickets (reference, project_id, demandeur_id, type, priorite, statut, titre, description, created_at, updated_at)
SELECT CONCAT('CNN-D', LPAD(n.n, 3, '0')), p.id, a.id, 'INCIDENT',
       ELT(1 + MOD(n.n, 4), 'CRITIQUE', 'ELEVEE', 'NORMALE', 'FAIBLE'),
       ELT(1 + MOD(n.n, 5), 'OUVERT', 'EN_COURS', 'EN_ATTENTE', 'RESOLU', 'CLOTURE'),
       CONCAT('Incident opérationnel ', n.n),
       'Ticket de supervision Colis na Nga.',
       DATE_SUB(NOW(), INTERVAL MOD(n.n, 30) DAY) + INTERVAL MOD(n.n, 20) HOUR,
       DATE_ADD(DATE_SUB(NOW(), INTERVAL MOD(n.n, 30) DAY), INTERVAL (MOD(n.n, 20) + (n.n * 2)) HOUR)
FROM projects p
JOIN agents a ON a.email_pro = 'support.cnn@mindsetalliance.cd'
JOIN (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7
    UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
    UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION SELECT 21
    UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28
) n
WHERE p.code = 'CNN';

INSERT INTO ticket_sequences (project_id, derniere_valeur)
SELECT p.id, 28 FROM projects p WHERE p.code = 'CNN'
ON DUPLICATE KEY UPDATE derniere_valeur = GREATEST(derniere_valeur, 28);

INSERT IGNORE INTO presences (agent_id, jour, type, heures)
SELECT a.id, DATE_SUB(CURDATE(), INTERVAL n.n DAY), 'PRESENT', 8
FROM agents a
JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE a.email_pro IN ('rh.demo@mindsetalliance.cd', 'support.cnn@mindsetalliance.cd', 'finance.demo@mindsetalliance.cd');

UPDATE ops_records
SET updated_at = DATE_ADD(created_at, INTERVAL 6 HOUR)
WHERE module = 'INVOICE' AND canal IS NOT NULL;
