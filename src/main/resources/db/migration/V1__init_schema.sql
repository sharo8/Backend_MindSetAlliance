-- V1 — schéma initial Core (MySQL 8 / MariaDB 10.11+)
-- Convention : V{n}__{module}_{action}.sql  (ex. V3__tickets_add_sla.sql)
-- Idempotent : IF NOT EXISTS pour rejouer après une migration marquée failed.

CREATE TABLE IF NOT EXISTS departements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) UNIQUE NOT NULL,
    nom VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) UNIQUE NOT NULL,
    nom VARCHAR(100) NOT NULL,
    description TEXT,
    statut VARCHAR(20) NOT NULL,
    date_lancement DATE,
    devise_gestion VARCHAR(3) DEFAULT 'USD',
    backend_url VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email_pro VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    departement_id BIGINT,
    statut VARCHAR(20) NOT NULL,
    two_factor_enabled TINYINT(1) DEFAULT 0,
    totp_secret VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agents_departement FOREIGN KEY (departement_id) REFERENCES departements(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(50) UNIQUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT,
    role_id BIGINT,
    project_id BIGINT,
    date_debut DATE NOT NULL DEFAULT (CURRENT_DATE),
    date_fin DATE,
    CONSTRAINT fk_ar_agent FOREIGN KEY (agent_id) REFERENCES agents(id),
    CONSTRAINT fk_ar_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_ar_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @uq_agent_roles := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'agent_roles' AND index_name = 'uq_agent_roles'
);
SET @sql_uq_agent_roles := IF(
    @uq_agent_roles = 0,
    'CREATE UNIQUE INDEX uq_agent_roles ON agent_roles (agent_id, role_id, (IFNULL(project_id, 0)))',
    'SELECT 1'
);
PREPARE stmt_uq_agent_roles FROM @sql_uq_agent_roles;
EXECUTE stmt_uq_agent_roles;
DEALLOCATE PREPARE stmt_uq_agent_roles;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT,
    action VARCHAR(50) NOT NULL,
    objet_type VARCHAR(50) NOT NULL,
    objet_id BIGINT,
    valeur_avant JSON,
    valeur_apres JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rt_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auth_challenges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    challenge_id VARCHAR(64) UNIQUE NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_ac_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_sequences (
    project_id BIGINT PRIMARY KEY,
    derniere_valeur INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ts_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(20) UNIQUE NOT NULL,
    project_id BIGINT NOT NULL,
    departement_destinataire_id BIGINT,
    demandeur_id BIGINT NOT NULL,
    attributaire_id BIGINT,
    `type` VARCHAR(30) NOT NULL,
    priorite VARCHAR(20) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'OUVERT',
    titre VARCHAR(200) NOT NULL,
    description TEXT,
    motif_annulation TEXT,
    echeance DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_t_dep FOREIGN KEY (departement_destinataire_id) REFERENCES departements(id),
    CONSTRAINT fk_t_dem FOREIGN KEY (demandeur_id) REFERENCES agents(id),
    CONSTRAINT fk_t_att FOREIGN KEY (attributaire_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_commentaires (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    auteur_id BIGINT NOT NULL,
    contenu TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tc_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_tc_auteur FOREIGN KEY (auteur_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_pieces_jointes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    nom_fichier VARCHAR(255) NOT NULL,
    type_mime VARCHAR(100),
    contenu LONGBLOB,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tpj_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dossiers_personnel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL UNIQUE,
    numero_identite VARCHAR(512),
    coordonnees_bancaires VARCHAR(1024),
    date_embauche DATE,
    salaire_base DECIMAL(14, 2),
    devise VARCHAR(3) DEFAULT 'USD',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dp_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS presences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    jour DATE NOT NULL,
    `type` VARCHAR(20) NOT NULL,
    heures DECIMAL(5, 2) DEFAULT 0,
    UNIQUE (agent_id, jour),
    CONSTRAINT fk_pr_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    `type` VARCHAR(30) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'DEMANDE',
    motif TEXT,
    CONSTRAINT fk_cg_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bulletins_paie (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    periode_annee INT NOT NULL,
    periode_mois INT NOT NULL,
    salaire_brut DECIMAL(14, 2) NOT NULL,
    net_a_payer DECIMAL(14, 2) NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'USD',
    statut VARCHAR(20) NOT NULL DEFAULT 'CALCULE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_id, periode_annee, periode_mois),
    CONSTRAINT fk_bp_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS elements_paie (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bulletin_id BIGINT NOT NULL,
    `type` VARCHAR(30) NOT NULL,
    libelle VARCHAR(200) NOT NULL,
    montant DECIMAL(14, 2) NOT NULL,
    CONSTRAINT fk_ep_bulletin FOREIGN KEY (bulletin_id) REFERENCES bulletins_paie(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS taux_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    devise_source VARCHAR(3) NOT NULL,
    devise_cible VARCHAR(3) NOT NULL,
    taux DECIMAL(18, 8) NOT NULL,
    date_effet DATE NOT NULL DEFAULT (CURRENT_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ecritures_financieres (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    `type` VARCHAR(20) NOT NULL,
    libelle VARCHAR(200) NOT NULL,
    montant DECIMAL(14, 2) NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'USD',
    statut VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    motif_annulation TEXT,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_at DATETIME,
    CONSTRAINT fk_ef_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_ef_agent FOREIGN KEY (created_by) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    annee INT NOT NULL,
    libelle VARCHAR(200) NOT NULL,
    montant_prevu DECIMAL(14, 2) NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'USD',
    CONSTRAINT fk_bd_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS factures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    numero VARCHAR(40) UNIQUE NOT NULL,
    montant DECIMAL(14, 2) NOT NULL,
    devise VARCHAR(3) NOT NULL DEFAULT 'USD',
    statut VARCHAR(20) NOT NULL DEFAULT 'EMISE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fa_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT,
    canal VARCHAR(20) NOT NULL,
    titre VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    lu TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nt_agent FOREIGN KEY (agent_id) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    titre VARCHAR(200) NOT NULL,
    categorie VARCHAR(50),
    project_id BIGINT,
    nom_fichier VARCHAR(255) NOT NULL,
    contenu LONGBLOB,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_doc_agent FOREIGN KEY (created_by) REFERENCES agents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vitrine_kpis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    categorie VARCHAR(50) NOT NULL,
    cle VARCHAR(100) NOT NULL,
    valeur DECIMAL(18, 4),
    derniere_maj DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, categorie, cle),
    CONSTRAINT fk_vk_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
