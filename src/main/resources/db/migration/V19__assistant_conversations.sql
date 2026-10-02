-- Historique Assistant IA : une conversation n’appartient qu’à un agent.
-- Pas de DELETE physique : archive = 1.

CREATE TABLE IF NOT EXISTS assistant_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    titre VARCHAR(180) NOT NULL,
    messages JSON NOT NULL,
    archive TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_activity_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_asst_conv_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_asst_conv_agent_activity
    ON assistant_conversations (agent_id, archive, last_activity_at);
