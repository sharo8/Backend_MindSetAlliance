-- Événements reçus des projets externes (Colis na Nga…) par HTTP signé.
-- Sert au dédoublonnage : un même event_id n'est jamais compté deux fois,
-- même si l'émetteur le renvoie après un délai d'attente.
CREATE TABLE integration_inbox (
    event_id     VARCHAR(64)  NOT NULL,
    source       VARCHAR(20)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    type         VARCHAR(60)  NULL,
    received_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    INDEX idx_integration_inbox_received (received_at)
);
