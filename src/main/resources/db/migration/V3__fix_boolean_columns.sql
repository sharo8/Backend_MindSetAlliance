-- Aligne les booléens JPA (preferred_boolean_jdbc_type=TINYINT)
-- sur TINYINT(1) : la base actuelle a BIT (BOOLEAN MySQL / création antérieure).

ALTER TABLE agents
    MODIFY COLUMN two_factor_enabled TINYINT(1) DEFAULT 0;

ALTER TABLE refresh_tokens
    MODIFY COLUMN revoked TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE auth_challenges
    MODIFY COLUMN consumed TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE notifications
    MODIFY COLUMN lu TINYINT(1) NOT NULL DEFAULT 0;
