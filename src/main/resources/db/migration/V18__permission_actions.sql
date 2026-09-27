ALTER TABLE permissions
    ADD COLUMN decomposable TINYINT(1) NOT NULL DEFAULT 0;

UPDATE permissions
SET decomposable = 1
WHERE code LIKE 'MANAGE_%'
  AND code NOT IN ('MANAGE_OWN_PROFILE', 'MANAGE_ACCESS', 'MANAGE_PERMISSIONS');

CREATE TABLE IF NOT EXISTS agent_permission_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    permission_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    allowed TINYINT(1) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_apa_agent FOREIGN KEY (agent_id) REFERENCES agents(id),
    CONSTRAINT fk_apa_perm FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT fk_apa_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT chk_apa_action CHECK (action IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @uq_apa := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'agent_permission_actions' AND index_name = 'uq_agent_permission_actions'
);
SET @sql_uq_apa := IF(
    @uq_apa = 0,
    'CREATE UNIQUE INDEX uq_agent_permission_actions ON agent_permission_actions (agent_id, permission_id, action, (IFNULL(project_id, 0)))',
    'SELECT 1'
);
PREPARE stmt_uq_apa FROM @sql_uq_apa;
EXECUTE stmt_uq_apa;
DEALLOCATE PREPARE stmt_uq_apa;
