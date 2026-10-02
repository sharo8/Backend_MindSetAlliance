# Backend Core — Mindset Alliance

IAM, JWT/JWKS, tickets, RH/paie, finance, documents, notifications, audit et vitrine consolidée. **Aucune donnée métier** des projets externes n'est stockée ici : seulement des projections (compteurs de la vitrine).

## Lancer isolément

MySQL 8 sur le port **3308**, base **`mindsetalliance_core`**.

Secrets via l'environnement ou un fichier `.env` (ignoré par Git) — voir `.env.example` :

```
DATABASE_USERNAME=root
DATABASE_PASSWORD=...        # obligatoire : plus aucun mot de passe par défaut dans le code
MA_AES_KEY=...               # 32 octets en Base64
```

Si Flyway signale une migration échouée :

```sql
DELETE FROM flyway_schema_history WHERE success = 0;
```

En profil `local`, `repair` est lancé automatiquement au démarrage.

```bash
mvn spring-boot:run
```

Swagger : http://localhost:8081/swagger-ui.html — sans Kafka : `mvn spring-boot:run -Dma.kafka.enabled=false`

---

## ⚠️ Sécurité — clé de signature des jetons (à lire)

La clé privée RSA qui signe **tous** les jetons (`data/rsa.jwk`) avait été commitée dans ce dépôt public. Quiconque la possède peut fabriquer un jeton « ADMIN_SYSTEME » valide.

Cette branche :

- retire `data/rsa.jwk` du dépôt et ajoute `data/` au `.gitignore` ;
- **refuse** désormais l'ancienne clé (`kid = ma-core-persistent`) : au premier démarrage, une nouvelle paire est générée automatiquement (les agents devront simplement se reconnecter) ;
- permet de fournir la clé en production par variable d'environnement, sans aucun fichier :

```
MA_AUTH_RSA_JWK={"kty":"RSA","kid":"ma-core-2026-...","n":"...","e":"AQAB","d":"...",...}
```

Chaque nouvelle clé reçoit un `kid` unique : les services qui mettent la JWKS en cache (Colis na Nga) détectent le changement et rechargent seuls.

> L'ancienne clé reste lisible dans l'historique Git. Elle est désormais rejetée par le code, mais pour faire propre on peut aussi réécrire l'historique (`git filter-repo --path data/rsa.jwk --invert-paths`) ou recréer le dépôt.

Autres corrections : mot de passe MySQL retiré de `application.yml` / `application-local.yml` ; `/api/auth/revocations` n'exige plus de jeton d'agent en plus de la clé interne (il était donc inaccessible aux services).

---

## Intégration Colis na Nga (CNN)

### Principe

```
 Console MA (navigateur)
        │  jeton de session de l'agent
        ▼
 ┌──────────────┐  ① GET/POST /api/cnn/**        ┌──────────────────┐
 │   CORE (MA)  │ ─────────────────────────────▶ │  COLIS NA NGA    │
 │              │  jeton court (60 s) signé      │  /api/ma/**      │
 │  vitrine,    │  aud = colis-na-nga            │                  │
 │  RBAC, audit │ ◀───────────────────────────── │  integration_    │
 │              │  ② POST /api/internal/cnn/     │  outbox          │
 └──────────────┘     events (lots signés HMAC)  └──────────────────┘
        ▲
        └──── ③ rattrapage nocturne : GET /api/ma/kpis/resume → totaux
```

Chaque backend garde **sa propre base**. Deux canaux seulement, tous deux authentifiés et signés.

### ① Lectures : le Core est la seule porte d'entrée

La console n'appelle **jamais** Colis na Nga directement. Elle appelle le Core :

```
GET  /api/cnn/courses?statut=DELIVERED&du=2026-09-01&au=2026-09-30&page=0&size=50
GET  /api/cnn/courses/{id}
GET  /api/cnn/fleet/temps-reel
GET  /api/cnn/coursiers?statut=ASSOCIATION_VALIDATED
GET  /api/cnn/coursiers/{id}
POST /api/cnn/coursiers/{id}/valider
GET  /api/cnn/incidents
GET  /api/cnn/abonnements
GET  /api/cnn/kpis/resume?date=2026-09-26
```

Pour chaque appel, le Core (`integration/cnn/CnnGatewayController`) :

1. vérifie le jeton de session de l'agent (chaîne de sécurité habituelle) ;
2. vérifie **en base** que l'agent est toujours `ACTIF` → une désactivation coupe l'accès immédiatement, sans attendre l'expiration du jeton ni `/revocations` ;
3. vérifie que l'agent voit le projet CNN ;
4. émet un **jeton de service de 60 s** : même `sub`/`email`, uniquement les rôles CNN ou transverses, `aud = colis-na-nga`, `jti` unique ;
5. relaie vers `<CNN>/api/ma/<chemin>` et renvoie la réponse telle quelle. Les écritures (`POST`) sont inscrites au journal d'audit.

Colis na Nga revérifie la signature (clé publique JWKS), l'émetteur, l'audience et les rôles : **deux contrôles indépendants**. Un jeton de session volé dans un navigateur n'ouvre rien chez CNN (mauvaise audience).

| Endpoint CNN | Rôles autorisés (+ `ADMIN_SYSTEME`, `DIRECTION`) |
|---|---|
| `fleet/temps-reel` | SUPPORT |
| `courses`, `courses/{id}` | SUPPORT, FINANCE (adresses masquées pour FINANCE) |
| `coursiers`, `coursiers/{id}` | COMMERCIAL, SUPPORT (téléphones masqués) |
| `coursiers/{id}/valider` | COMMERCIAL — uniquement après validation par l'association |
| `incidents` | SUPPORT |
| `abonnements` | FINANCE |
| `kpis/resume` | toute lecture + `SYSTEME` (le Core lui-même) |

### Rapidité

- **Connexions réutilisées** : un seul client HTTP partagé (keep-alive), pas de nouvelle poignée de main TLS à chaque requête.
- **Cache de 10 s** sur les lectures, par combinaison de rôles : les rafraîchissements répétés de la console ne touchent pas CNN. Une écriture vide le cache.
- **Aucun appel réseau pour vérifier un jeton** chez CNN : la clé publique est en cache.
- **Maintien à chaud** : un hébergeur gratuit (Render free) endort CNN après ~15 min, et le réveil prend ~1 min. Le Core le ping toutes les 10 min (`MA_CNN_KEEP_WARM`). Sur un plan payant, on peut le désactiver.
- **Délais courts** : connexion 3 s, réponse 20 s, puis `502`/`504` explicites au lieu d'une attente sans fin.
- Réponses JSON compressées vers le navigateur.

### ② Événements : HTTP signé, garanti sans perte ni doublon

Remplace Kafka **sans changer le format** des événements (mêmes topics, même JSON). Kafka reste possible plus tard : `ProjectEventListener` et l'ingestion HTTP passent par le même `ProjectEventProcessor`.

```
POST /api/internal/cnn/events
X-MA-Internal-Key: <MA_INTERNAL_API_KEY>
X-CNN-Timestamp:   1790441295
X-CNN-Signature:   sha256=<hex HMAC-SHA256(MA_CNN_WEBHOOK_SECRET, timestamp + "." + corps)>

{"events":[
  {"eventId":"4b1c…","topic":"colisnanga.courses.events",
   "payload":{"type":"COURSE_LIVREE","courseId":"…","montant":3500,"horodatage":"2026-09-26T14:32:00Z"}}
]}
→ 200 {"accepted":["4b1c…"],"duplicates":[],"failed":[]}
```

| Garantie | Mécanisme |
|---|---|
| Authenticité / intégrité | HMAC-SHA256 du corps brut + clé interne |
| Anti-rejeu | horodatage signé à ±5 min de l'heure du Core |
| Exactement une fois | `eventId` mémorisé dans `integration_inbox` (migration V20) dans la même transaction que la mise à jour des KPI |
| Pas de perte | CNN écrit l'événement dans sa table `integration_outbox` dans la même transaction que le fait métier, et le renvoie tant qu'il n'est pas dans `accepted` ou `duplicates` |
| Cloisonnement | `projectCode` forcé à `CNN` : ce canal ne peut pas modifier les KPI d'un autre projet |

Types publiés par CNN : `COURSE_CREEE`, `COURSE_LIVREE`, `COURSE_REFUSEE` (refus ou non-réponse du motard), `COURSE_ANNULEE`, `INCIDENT_SIGNALE` (appel d'urgence → crée aussi un ticket SUPPORT), `COURSIER_VALIDE`, `ABONNEMENT_REGLE` (+ montant dans `FINANCE/abonnements_montant`).

> Ne pas utiliser en plus `POST /api/internal/cnn/incidents` pour les mêmes incidents : `INCIDENT_SIGNALE` crée déjà le ticket (on aurait des doublons).

### ③ Rattrapage automatique

Chaque nuit à 03:15 (Kinshasa) et 2 min après chaque démarrage, le Core lit `GET /api/ma/kpis/resume` avec un jeton `SYSTEME` et recopie les `totaux` officiels de CNN dans `vitrine_kpis`. Si un événement s'est perdu, la vitrine se corrige toute seule.

### Configuration

| Variable (Core) | Rôle |
|---|---|
| `MA_CNN_ENABLED` | `true` pour activer le relais, le maintien à chaud et le rattrapage |
| `MA_CNN_BASE_URL` | URL de CNN, ex. `https://colis-na-nga-api.onrender.com` |
| `MA_CNN_WEBHOOK_SECRET` | secret HMAC partagé, **≥ 32 caractères** (sinon tout événement est refusé) |
| `MA_INTERNAL_API_KEY` | clé exigée sur `/api/internal/**` et `/api/auth/revocations` |
| `MA_CNN_CACHE_SECONDS` | durée du cache des lectures (défaut 10, 0 = désactivé) |
| `MA_CNN_KEEP_WARM` | ping toutes les 10 min (défaut `true`) |
| `MA_AUTH_RSA_JWK` | clé de signature en production (voir plus haut) |

Côté Colis na Nga (même secret et même clé des deux côtés) :

```
MA_CORE_JWKS_URL=https://<core>/api/auth/.well-known/jwks.json
MA_CORE_ISSUER=mindset-alliance-core
MA_EVENTS_ENABLED=true
MA_CORE_EVENTS_URL=https://<core>/api/internal/cnn/events
MA_CORE_INTERNAL_KEY=<= MA_INTERNAL_API_KEY du Core>
MA_WEBHOOK_SECRET=<= MA_CNN_WEBHOOK_SECRET du Core>
```

Générer un secret : `openssl rand -hex 32`.

### Mise en service pas à pas

1. Déployer le Core avec `MA_AUTH_RSA_JWK`, `MA_INTERNAL_API_KEY`, `MA_CNN_WEBHOOK_SECRET`, `MA_CNN_BASE_URL`, `MA_CNN_ENABLED=true`.
2. Déployer CNN avec les variables ci-dessus.
3. Se connecter à MA Workspace avec un agent SUPPORT et appeler `GET /api/cnn/kpis/resume` : réponse de CNN attendue.
4. Livrer une course de test dans l'app : `courses_livrees` augmente dans `/api/vitrine` en quelques secondes.
5. Vérifier les journaux : côté CNN, chaque accès MA est tracé (`MA_ACCESS ACCES 200 …` / `REFUS 403 …`).

### Frontend MA Workspace

Remplacer l'écran « Supervision Colis na Nga » (aujourd'hui alimenté par `/api/ops/dashboards/cnn`, données de démo `source: CORE_DEMO`) par des appels à `/api/cnn/...`, par exemple `GET /api/cnn/kpis/resume` pour les tuiles et `GET /api/cnn/courses` pour la liste.

### Tests

```bash
mvn test -Dtest=CnnEventIngestServiceTest,ProjectEventListenerContractTest
```

Couvrent : événement signé traité une seule fois, signature falsifiée refusée, rejeu hors fenêtre refusé, secret absent → tout refusé.

JWT agent : 15 minutes, `sub` = `agent-id-{id}`, claim `roles` = `[{projectCode, role}]`, `iss` = `mindset-alliance-core`.
