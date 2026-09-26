ALTER TABLE tickets
    ADD COLUMN type_autre_precision VARCHAR(120) NULL AFTER type,
    ADD COLUMN archive TINYINT(1) NOT NULL DEFAULT 0 AFTER sla_alerte_envoyee;

CREATE TABLE IF NOT EXISTS ticket_statut_historique (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    statut_avant VARCHAR(40) NULL,
    statut_apres VARCHAR(40) NOT NULL,
    agent_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tsh_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_tsh_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ticket_statut_historique (ticket_id, statut_avant, statut_apres, agent_id, created_at)
SELECT id, NULL, statut, demandeur_id, created_at FROM tickets;
