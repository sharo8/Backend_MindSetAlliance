-- Univers tickets : SUPPORT (incidents ops) vs TECHNIQUE (bugs & déploiements)
ALTER TABLE tickets
    ADD COLUMN systeme VARCHAR(20) NOT NULL DEFAULT 'SUPPORT' AFTER `type`,
    ADD COLUMN categorie VARCHAR(40) NULL AFTER systeme,
    ADD COLUMN source_externe VARCHAR(40) NULL AFTER categorie,
    ADD COLUMN source_reference VARCHAR(80) NULL AFTER source_externe;

CREATE UNIQUE INDEX uk_tickets_source_reference ON tickets (source_reference);

UPDATE tickets t
JOIN projects p ON p.id = t.project_id AND p.code = 'CNN'
SET
    t.systeme = 'SUPPORT',
    t.categorie = ELT(1 + MOD(CAST(SUBSTRING(t.reference, 6) AS UNSIGNED) - 1, 4),
        'REFUS_COURSE', 'RETARD', 'LITIGE_PAIEMENT', 'DESTINATAIRE_INJOIGNABLE'),
    t.titre = CASE 1 + MOD(CAST(SUBSTRING(t.reference, 6) AS UNSIGNED) - 1, 4)
        WHEN 1 THEN CONCAT('Refus de course — Course #', 4500 + CAST(SUBSTRING(t.reference, 6) AS UNSIGNED))
        WHEN 2 THEN CONCAT('Retard livraison — Course #', 4500 + CAST(SUBSTRING(t.reference, 6) AS UNSIGNED))
        WHEN 3 THEN CONCAT('Litige paiement Mobile Money — Client ', CHAR(64 + (1 + MOD(CAST(SUBSTRING(t.reference, 6) AS UNSIGNED), 12))))
        ELSE CONCAT('Destinataire injoignable — Course #', 1100 + CAST(SUBSTRING(t.reference, 6) AS UNSIGNED))
    END,
    t.description = CASE 1 + MOD(CAST(SUBSTRING(t.reference, 6) AS UNSIGNED) - 1, 4)
        WHEN 1 THEN 'Incident Support : le coursier a refusé la course.'
        WHEN 2 THEN 'Incident Support : retard de livraison constaté.'
        WHEN 3 THEN 'Incident Support : litige de paiement Mobile Money.'
        ELSE 'Incident Support : destinataire injoignable.'
    END
WHERE t.reference LIKE 'CNN-D%';

UPDATE tickets SET systeme = 'SUPPORT' WHERE systeme IS NULL OR systeme = '';

INSERT IGNORE INTO tickets (reference, project_id, demandeur_id, `type`, systeme, categorie, priorite, statut, titre, description, created_at, updated_at)
SELECT CONCAT('CNN-T', LPAD(n.n, 3, '0')), p.id, a.id, 'ANOMALIE_TECHNIQUE', 'TECHNIQUE',
       ELT(n.n, 'BUG', 'DEPLOIEMENT', 'CORRECTIF', 'AUTRE', 'BUG', 'DEPLOIEMENT'),
       ELT(1 + MOD(n.n, 4), 'CRITIQUE', 'ELEVEE', 'NORMALE', 'FAIBLE'),
       ELT(1 + MOD(n.n, 3), 'OUVERT', 'EN_COURS', 'RESOLU'),
       ELT(n.n,
           'Bug — timeout API courses CNN',
           'Déploiement — release tracking v1.4',
           'Correctif — retry webhook paiement',
           'Autre — revue logs passerelle',
           'Bug — carte flotte hors sync',
           'Déploiement — migration Redis cache'),
       'Ticket technique interne (Bugs & Déploiements).',
       DATE_SUB(NOW(), INTERVAL n.n DAY),
       NOW()
FROM projects p
JOIN agents a ON a.email_pro = 'dev@mindsetalliance.cd'
JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE p.code = 'CNN';
