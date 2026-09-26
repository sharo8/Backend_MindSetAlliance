-- V9 — compte administrateur réel, 2FA e-mail, premier mot de passe obligatoire
-- Hash BCrypt généré hors dépôt (mot de passe temporaire jamais stocké en clair).

ALTER TABLE agents
    ADD COLUMN doit_changer_mot_de_passe TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN welcome_email_sent TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE auth_challenges
    ADD COLUMN code_hash VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS password_setup_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_pst_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO agents (email_pro, password_hash, nom, prenom, departement_id, statut, two_factor_enabled, doit_changer_mot_de_passe)
SELECT 'alinekabacele@gmail.com',
       '$2b$12$NJPgiQVGG9Nh2anEtRxCvus1JVS0tY2h8l6vVlav.V8pjrM7gGbN6',
       'Kabacele',
       'Aline',
       (SELECT id FROM departements WHERE code = 'DEP-08'),
       'ACTIF',
       1,
       1
WHERE NOT EXISTS (SELECT 1 FROM agents WHERE email_pro = 'alinekabacele@gmail.com');

INSERT INTO agent_roles (agent_id, role_id, project_id)
SELECT a.id, r.id, NULL
FROM agents a, roles r
WHERE a.email_pro = 'alinekabacele@gmail.com'
  AND r.nom = 'ADMIN_SYSTEME'
  AND NOT EXISTS (
      SELECT 1 FROM agent_roles ar WHERE ar.agent_id = a.id AND ar.role_id = r.id
  );

UPDATE agents
SET statut = 'INACTIF'
WHERE email_pro = 'admin.demo@mindsetalliance.cd';
