-- CDC 4.3 : historique de réattribution, liaisons, SLA, catégorie incident
ALTER TABLE tickets
    ADD COLUMN premiere_prise_en_charge_at DATETIME NULL AFTER echeance,
    ADD COLUMN resolu_at DATETIME NULL AFTER premiere_prise_en_charge_at,
    ADD COLUMN sla_alerte_envoyee TINYINT(1) NOT NULL DEFAULT 0 AFTER resolu_at,
    ADD COLUMN objet_lie_type VARCHAR(40) NULL AFTER sla_alerte_envoyee,
    ADD COLUMN objet_lie_id VARCHAR(80) NULL AFTER objet_lie_type,
    ADD COLUMN objet_lie_libelle VARCHAR(200) NULL AFTER objet_lie_id;

UPDATE tickets SET statut = 'EN_COURS' WHERE statut = 'PRIS_EN_CHARGE';

UPDATE tickets
SET type = 'INCIDENT'
WHERE (type IS NULL OR type = '') AND categorie IN ('REFUS_COURSE', 'RETARD', 'LITIGE_PAIEMENT', 'DESTINATAIRE_INJOIGNABLE');

UPDATE tickets
SET type = 'ANOMALIE_TECHNIQUE'
WHERE systeme = 'TECHNIQUE' AND (type IS NULL OR type = 'ANOMALIE_TECHNIQUE' OR type = '');

UPDATE tickets SET type = 'DEMANDE_TRAVAIL' WHERE type IS NULL OR type = '';

CREATE TABLE IF NOT EXISTS ticket_reattributions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    ancien_agent_id BIGINT NULL,
    nouvel_agent_id BIGINT NOT NULL,
    auteur_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tr_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_tr_ancien FOREIGN KEY (ancien_agent_id) REFERENCES agents(id),
    CONSTRAINT fk_tr_nouvel FOREIGN KEY (nouvel_agent_id) REFERENCES agents(id),
    CONSTRAINT fk_tr_auteur FOREIGN KEY (auteur_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
