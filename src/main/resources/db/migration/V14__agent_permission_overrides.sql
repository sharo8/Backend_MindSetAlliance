-- Dérogations individuelles (GRANT / DENY) au-dessus des rôles.
CREATE TABLE IF NOT EXISTS agent_permission_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    type VARCHAR(10) NOT NULL,
    motif TEXT NOT NULL,
    accorde_par BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_apo_agent FOREIGN KEY (agent_id) REFERENCES agents(id),
    CONSTRAINT fk_apo_perm FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT fk_apo_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_apo_by FOREIGN KEY (accorde_par) REFERENCES agents(id),
    CONSTRAINT chk_apo_type CHECK (type IN ('GRANT', 'DENY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @uq_apo := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'agent_permission_overrides' AND index_name = 'uq_agent_permission_overrides'
);
SET @sql_uq_apo := IF(
    @uq_apo = 0,
    'CREATE UNIQUE INDEX uq_agent_permission_overrides ON agent_permission_overrides (agent_id, permission_id, (IFNULL(project_id, 0)))',
    'SELECT 1'
);
PREPARE stmt_uq_apo FROM @sql_uq_apo;
EXECUTE stmt_uq_apo;
DEALLOCATE PREPARE stmt_uq_apo;
