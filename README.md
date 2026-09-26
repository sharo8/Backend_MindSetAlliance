# Backend Core — Mindset Alliance

IAM, JWT/JWKS, tickets, RH/paie, finance, documents, notifications, audit et vitrine (projections Kafka). **Aucune donnée métier** des projets externes.

## Lancer isolément

MySQL 8 sur le port **3308**, base **`mindsetalliance_core`**.

Secrets via l'environnement (jamais dans le dépôt) :

```
DATABASE_USERNAME=root
DATABASE_PASSWORD=...
MA_AES_KEY=...   # 32 octets en Base64
```

Si Flyway signale une migration V1 échouée :

```sql
DELETE FROM flyway_schema_history WHERE success = 0;
```

(voir `docs/flyway-repair.sql`). En profil `local`, `repair` est lancé automatiquement au démarrage.

```bash
mvn spring-boot:run
```

Swagger : http://localhost:8081/swagger-ui.html

Sans Kafka :

```bash
mvn spring-boot:run -Dma.kafka.enabled=false
```

## Intégrations externes

Les backends Colis na Nga et Mindset du Royaume ne sont pas dans ce dépôt. Ils :

- vérifient les JWT via `GET /api/auth/.well-known/jwks.json` ;
- publient des événements Kafka conformes à `docs/contrats-evenements.md`.

Le Core **consomme** uniquement ces événements pour alimenter `vitrine_kpis`. Un producteur de messages factices existe dans `src/test/` pour les tests d'intégration, pas en production.

JWT : 15 minutes, `sub` = `agent-id-{id}`, claim `roles` = `[{projectCode, role}]`.
