# Connecter le backend Colis na Nga au backend Mindset Alliance (MA Core)

Ce guide explique, pas à pas, comment MA Workspace lit et administre les données de
**Colis na Nga**. Il s'adresse à toute personne qui doit installer, déployer ou dépanner la liaison.

---

## Sommaire

1. [Principe en une minute](#1-principe-en-une-minute)
2. [Les trois canaux de la liaison](#2-les-trois-canaux-de-la-liaison)
3. [Canal 1 : lecture seule de la base Colis na Nga](#3-canal-1--lecture-seule-de-la-base-colis-na-nga)
4. [Canal 2 : le relais d'administration (écritures)](#4-canal-2--le-relais-dadministration-écritures)
5. [Canal 3 : événements et incidents (facultatif)](#5-canal-3--événements-et-incidents-facultatif)
6. [Permissions MA et ressources Colis na Nga](#6-permissions-ma-et-ressources-colis-na-nga)
7. [Tracé GPS des commandes](#7-tracé-gps-des-commandes)
8. [Mise en place complète, étape par étape](#8-mise-en-place-complète-étape-par-étape)
9. [Dépannage](#9-dépannage)
10. [Fichiers concernés](#10-fichiers-concernés)

---

## 1. Principe en une minute

- MA Workspace **lit** les données Colis na Nga directement dans la base MySQL de Colis na Nga,
  avec un utilisateur qui n'a **que le droit `SELECT`**.
- MA Workspace **ne modifie jamais** la base Colis na Nga directement. Toute modification
  (créer un abonnement, désactiver un client, changer un tarif…) est envoyée à
  **l'API admin de Colis na Nga**, comme si un administrateur de l'application l'avait faite.
  Les règles métier, notifications (SMS, push) et journaux d'audit de Colis na Nga s'appliquent donc.
- Chaque appel est autorisé par une **permission MA** de l'agent connecté, puis signé par MA Core.

```
 Agent MA ──► Frontend MA ──► MA Core ──┬──(SELECT, ma_reader)──────────► Base colisnanga (Cloud SQL)
                                        │
                                        └──(X-MA-Relay, RS256 60 s)────► colis-backend /api/admin/**
                                                                          (compte de service MA-WORKSPACE)
```

| Élément | Valeur |
|---|---|
| Projet Google Cloud | `colis-na-ngai` |
| Service Cloud Run | `colis-backend` (région `europe-west1`) |
| Instance Cloud SQL | `colis-na-ngai:europe-west1:colis-db` |
| Base | `colisnanga` |
| Utilisateur lecture seule | `ma_reader` |
| Émetteur des jetons MA | `mindset-alliance-core` |
| Audience attendue par Colis na Nga | `colis-na-nga` |
| Compte de service Colis na Nga | téléphone `MA-WORKSPACE` (rôle SUPER_ADMIN, nom « Mindset Alliance Workspace ») |

---

## 2. Les trois canaux de la liaison

| Canal | Sens | Sert à | Obligatoire |
|---|---|---|---|
| 1. Lecture base | MA ← Colis na Nga | tableau de bord, listes, carte, itinéraires, statistiques | oui |
| 2. Relais admin | MA → Colis na Nga | toutes les modifications | oui pour modifier |
| 3. Événements | Colis na Nga → MA | incidents vers la billetterie MA, indicateurs | non |

---

## 3. Canal 1 : lecture seule de la base Colis na Nga

### 3.1 Créer l'utilisateur `ma_reader` (une seule fois, par un administrateur de la base)

Se connecter à l'instance Cloud SQL avec un compte administrateur, puis :

```sql
CREATE USER 'ma_reader'@'%' IDENTIFIED BY '<mot de passe fort, gardé hors de Git>';
GRANT SELECT, SHOW VIEW ON colisnanga.* TO 'ma_reader'@'%';
FLUSH PRIVILEGES;
```

Ne jamais lui donner `INSERT`, `UPDATE`, `DELETE` ni `ALL`. Au démarrage, MA Core exécute
`SHOW GRANTS` (`CnnGrantVerifier`) et signale tout droit supplémentaire.

### 3.2 Ouvrir l'accès réseau : Cloud SQL Auth Proxy

En local (ou sur une VM), le proxy expose la base Cloud SQL sur un port local :

```bash
gcloud auth application-default login      # une fois, avec un compte qui a le rôle « Client Cloud SQL »
~/cloud-sql-proxy --port 3310 colis-na-ngai:europe-west1:colis-db
```

Laisser ce terminal ouvert. Sur Cloud Run, utiliser plutôt la connexion Cloud SQL intégrée
(`--add-cloudsql-instances`) ou une IP privée.

### 3.3 Configurer MA Core

```bash
export MA_CNN_MODE=db
export MA_CNN_DB_URL='jdbc:mysql://127.0.0.1:3310/colisnanga?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Kinshasa'
export MA_CNN_DB_USERNAME=ma_reader
read -s -p "Mot de passe ma_reader : " MA_CNN_DB_PASSWORD; echo; export MA_CNN_DB_PASSWORD
export MA_CNN_STORAGE_ZONE=UTC          # Colis na Nga (Cloud Run) enregistre ses dates en UTC
```

Fonctionnement côté MA Core :

- Pool JDBC dédié (`CnnDataSourceConfig`) : 8 connexions, 3 gardées ouvertes, keepalive 60 s.
- Lectures en parallèle (`CnnParallel`), caches courts (synthèse 10 s, statistiques 60 s).
- `CnnWarmup` réveille la base et l'instance Cloud Run toutes les 4 minutes pour éviter l'attente du premier appel.
- « Aujourd'hui » est calculé à l'heure de Kinshasa.

### 3.4 Particularité du schéma Colis na Nga

Les clés étrangères générées par Hibernate dépendent de la clé primaire de la table cible
(exemples : `orders.client_user_id`, `motard_credit_subscriptions.motard_user_id`).
Ne jamais deviner un nom de colonne : utiliser `CnnLectureService.fk(table, candidats…)`,
qui lit le vrai nom dans `information_schema`. Les UUID stockés en `BINARY(16)` sont lus avec `BIN_TO_UUID`.

---

## 4. Canal 2 : le relais d'administration (écritures)

### 4.1 Déroulé d'un appel

1. Le navigateur appelle MA Core : `/api/cnn/admin/<ressource>/...`.
2. MA Core retrouve la permission de la ressource (`CnnAdminScope`) et l'action selon la méthode :

   | Méthode | Action MA vérifiée |
   |---|---|
   | `GET` | VIEW |
   | `POST` sur une collection | CREATE |
   | `POST` sur une ressource, `PUT`, `PATCH` | UPDATE |
   | `DELETE` | DELETE |

   `ADMIN_SYSTEME` et `DIRECTION` ont tout. Une dérogation « refusée » l'emporte toujours.
3. MA Core signe un **jeton RS256 valable 60 s** avec sa clé privée :

   | Claim | Valeur |
   |---|---|
   | `iss` | `mindset-alliance-core` |
   | `aud` | `colis-na-nga` |
   | `sub` | identifiant de l'agent MA |
   | `act` | e-mail de l'agent MA |
   | `cnnRelay` | `true` |
   | `jti` | identifiant unique |

4. MA Core appelle `<MA_CNN_API_BASE_URL>/api/admin/<ressource>/...` avec l'en-tête `X-MA-Relay: <jeton>`.
5. Côté Colis na Nga, `MaAdminRelayFilter` vérifie la signature (clé publique MA), l'émetteur, l'audience,
   l'expiration (tolérance 30 s) et `cnnRelay = true`, puis exécute la requête **au nom du compte de service
   `MA-WORKSPACE`** (SUPER_ADMIN).
6. Traces : journal `MA_ACCESS` côté Colis na Nga (avec l'agent MA réel), journal d'audit MA (`CNN_ADMIN_<ACTION>`).

État du relais : `GET /api/cnn/admin-relay/status` (MA Core) renvoie `relayReady`.
La sonde appelle `/api/admin/dashboard` de Colis na Nga ; résultat gardé 60 s s'il est bon, 15 s sinon.
Le frontend n'affiche les boutons de modification que si `relayReady` est vrai.

### 4.2 Variables côté Colis na Nga (`colis-backend`)

| Variable | Exemple / défaut | Rôle |
|---|---|---|
| `MA_RELAY_ENABLED` | `true` | active le relais (désactivé par défaut) |
| `MA_CORE_JWKS_URL` | `https://<domaine MA>/api/auth/.well-known/jwks.json` | **en production** : URL publique des clés de MA Core |
| `MA_CORE_JWK` | `{"kty":"RSA","e":"AQAB","kid":"...","n":"..."}` | **en local / sans URL publique** : la clé publique MA collée en JSON |
| `MA_CORE_ISSUER` | `mindset-alliance-core` | doit égaler `MA_AUTH_ISSUER` de MA Core |
| `MA_RELAY_ACCOUNT_PHONE` | `MA-WORKSPACE` | téléphone du compte de service (créé au démarrage par `DataInitializer`) |

Une seule des deux suffit : `MA_CORE_JWKS_URL` ou `MA_CORE_JWK`. L'URL est préférable : la rotation de clé est alors automatique.

### 4.3 Variables côté MA Core

| Variable | Rôle |
|---|---|
| `MA_CNN_API_BASE_URL` | URL de `colis-backend` (par défaut l'URL Cloud Run actuelle) |
| `MA_CNN_API_TIMEOUT_SECONDS` | délai maximum d'un appel relayé (défaut 20 s) |
| `MA_AUTH_RSA_KEYSTORE` | clé RSA qui signe aussi les jetons de relais |
| `MA_AUTH_ISSUER` | émetteur (défaut `mindset-alliance-core`) |

### 4.4 Récupérer la clé publique MA et la donner à Colis na Nga

MA Core démarré :

```bash
curl -s http://localhost:8081/api/auth/.well-known/jwks.json
```

La réponse ressemble à `{"keys":[{ ... }]}`. Il faut l'objet **à l'intérieur** du tableau `keys`
(public seulement : `kty`, `e`, `kid`, `n`, éventuellement `alg`, `use`). Avec `jq` :

```bash
MA_JWK=$(curl -s http://localhost:8081/api/auth/.well-known/jwks.json | jq -c '.keys[0]')
```

Puis mettre à jour Cloud Run. La clé contient des virgules : on change le séparateur de
`gcloud` avec la syntaxe `^##^` pour qu'elles ne soient pas coupées :

```bash
gcloud run services update colis-backend \
  --project colis-na-ngai --region europe-west1 \
  --update-env-vars "^##^MA_RELAY_ENABLED=true##MA_CORE_JWK=${MA_JWK}"
```

À refaire **à chaque changement de la clé RSA de MA** (fichier `data/rsa.jwk` supprimé ou remplacé).

### 4.5 Déployer le backend Colis na Nga

Depuis le dossier du backend Colis na Nga (branche `dev`) :

```bash
gcloud run deploy colis-backend --source . --region europe-west1 --project colis-na-ngai
```

Les variables déjà définies sur le service sont conservées.

---

## 5. Canal 3 : événements et incidents (facultatif)

Désactivé par défaut (`MA_EVENTS_ENABLED=false`). Colis na Nga écrit ses événements dans une table
**outbox** (`OutboxWriter`, sujets `colisnanga.courses.events` et `colisnanga.coursiers.events`),
puis `OutboxPublisher` les envoie par lots (100 événements, toutes les 5 s, avec nouvelles tentatives).

Chaque envoi porte :

| En-tête | Contenu |
|---|---|
| `X-MA-Internal-Key` | clé partagée (`MA_CORE_INTERNAL_KEY` côté CNN = `MA_INTERNAL_API_KEY` côté MA) |
| `X-CNN-Timestamp` | horodatage en secondes |
| `X-CNN-Signature` | `sha256=` + HMAC-SHA256 (`MA_WEBHOOK_SECRET`) de l'horodatage et du corps |

| Variable (Colis na Nga) | Rôle |
|---|---|
| `MA_EVENTS_ENABLED` | `true` pour activer l'envoi |
| `MA_CORE_EVENTS_URL` | URL de réception côté MA Core |
| `MA_CORE_INTERNAL_KEY` | clé interne partagée |
| `MA_WEBHOOK_SECRET` | secret de signature HMAC |

Côté MA Core :

- Toutes les routes `/api/internal/**` exigent `X-MA-Internal-Key` = `MA_INTERNAL_API_KEY` (`InternalKeyFilter`).
- `POST /api/internal/cnn/incidents` crée un ticket dans la billetterie MA à partir d'un incident Colis na Nga.
- Les indicateurs de la vitrine (`vitrine_kpis`) sont alimentés par Kafka (`ProjectEventListener`, `MA_KAFKA_ENABLED=true`).
- La réception des lots d'événements outbox n'a pas encore de route dédiée dans MA Core : laisser `MA_EVENTS_ENABLED=false` tant qu'elle n'existe pas.

L'API de lecture serveur à serveur `/api/ma/**` de Colis na Nga (`MaController` : flotte, courses, coursiers,
incidents, abonnements, KPI) accepte les jetons MA Core (`MaSecurityConfig`). MA Workspace utilise aujourd'hui
la lecture directe en base (canal 1), plus rapide et plus complète.

---

## 6. Permissions MA et ressources Colis na Nga

Permissions créées par la migration MA `V21` (module « Colis na Nga ») et ressources couvertes (`CnnAdminScope`) :

| Permission MA | Domaine dans MA Workspace |
|---|---|
| `VIEW_CNN_DASHBOARD` | tableau de bord, carte en direct, statistiques |
| `MANAGE_CNN_ORDERS` | commandes, livraisons, appels et contact d'urgence |
| `MANAGE_CNN_AGENTS` | livreurs |
| `MANAGE_CNN_MOTARDS` | motards partenaires, crédits, signalements |
| `MANAGE_CNN_ASSOCIATIONS` | associations, gains, reversements |
| `MANAGE_CNN_CLIENTS` | clients ponctuels et abonnés |
| `MANAGE_CNN_SUBSCRIPTIONS` | formules d'abonnement, factures |
| `MANAGE_CNN_PAYMENTS` | paiements Mobile Money, remboursements |
| `MANAGE_CNN_SETTINGS` | tarifs, taux de change |
| `MANAGE_CNN_ADMINS` | administrateurs et journal d'audit de l'application |

Les actions (VIEW, CREATE, UPDATE, DELETE) se règlent par agent dans **Administration → Accès**.
Pour ajouter une nouvelle ressource Colis na Nga au relais : la déclarer dans `CnnAdminScope` avec sa
permission, sinon MA Core la refuse.

---

## 7. Tracé GPS des commandes

Côté Colis na Nga :

- Table `order_track_points` (entité `OrderTrackPoint`).
- `GeolocationService` transmet chaque position du coursier à `OrderTrackRecorder`, qui enregistre un point
  pour chaque commande en cours **tous les 10 m parcourus, au plus toutes les 3 s**.

Côté MA Core : `GET /api/cnn/lecture/orders/{id}/itineraire` renvoie la commande, le coursier, les arrêts,
la chronologie (`status_histories`) et le tracé GPS. Le frontend aligne le trajet sur les routes avec
l'API Directions de Google. Les commandes antérieures à l'enregistrement n'ont que le trajet prévu.

---

## 8. Mise en place complète, étape par étape

1. **Base** : créer `ma_reader` (section 3.1).
2. **Proxy** : lancer `cloud-sql-proxy --port 3310 …` (section 3.2).
3. **MA Core** : exporter les variables `MA_CNN_*` puis `mvn spring-boot:run` (section 3.3).
   Vérifier les journaux : pas d'erreur `CnnGrantVerifier`, la synthèse se charge.
4. **Clé publique** : récupérer la JWK de MA (section 4.4).
5. **Colis na Nga** : `MA_RELAY_ENABLED=true` et `MA_CORE_JWK` (ou `MA_CORE_JWKS_URL`) sur Cloud Run (section 4.4).
6. **Vérifier** : `GET /api/cnn/admin-relay/status` → `relayReady: true` ; journal MA :
   « Relais Colis na Nga opérationnel. »
7. **Permissions** : donner aux agents concernés les permissions `*_CNN_*` utiles (section 6).
8. **Frontend** : choisir le projet « Colis na Nga » dans le sélecteur : tout le menu bascule.

---

## 9. Dépannage

| Symptôme | Cause | Solution |
|---|---|---|
| Relais : `401` « Jeton de relais MA invalide » | `MA_CORE_JWK` vide, mal collé, ou clé RSA MA changée | recopier la clé publique actuelle (section 4.4) |
| Relais : `403` « Relais MA désactivé » | `MA_RELAY_ENABLED` absent ou `false` | le passer à `true` sur Cloud Run |
| Relais : `503` « Compte de service MA indisponible » | compte `MA-WORKSPACE` non créé ou erreur à sa création (ex. mot de passe généré de plus de 72 octets refusé par BCrypt) | vérifier les journaux de démarrage de Colis na Nga (`DataInitializer`) puis redéployer |
| `relayReady` reste `false` | Cloud Run injoignable ou mauvais `MA_CNN_API_BASE_URL` | lire le motif dans les journaux MA (« Relais Colis na Nga indisponible : … ») |
| Proxy : `403 NOT_AUTHORIZED` | identifiants `gcloud` expirés ou rôle Client Cloud SQL manquant | `gcloud auth application-default login` |
| `Unknown column 's.motard_id'` (ou similaire) | nom de clé étrangère deviné | utiliser `fk()` ; les vrais noms sont `client_user_id`, `motard_user_id`… |
| Dates décalées d'une heure | fuseau | `MA_CNN_STORAGE_ZONE=UTC` ; le frontend lit les dates de l'API admin comme UTC (`parseCnnDate`) |
| Premier chargement lent | instance Cloud Run endormie | `CnnWarmup` la réveille toutes les 4 min ; vérifier qu'il tourne |
| Itinéraire non aligné sur les routes | Directions API non activée pour la clé web | voir le README du frontend (clé Google Maps) |

---

## 10. Fichiers concernés

**MA Core (ce dépôt)**

- `cnn/CnnProperties.java`, `cnn/CnnDataSourceConfig.java`, `cnn/CnnGrantVerifier.java`, `cnn/CnnReadOnlyClient.java`
- `cnn/CnnParallel.java`, `cnn/CnnWarmup.java`
- `cnn/admin/CnnAdminRelayController.java`, `cnn/admin/CnnAdminScope.java`
- `cnn/synthese/*` (synthèse, lecture, statistiques, taux)
- `config/InternalKeyFilter.java`, `tickets/CnnIncidentIngestController.java`, `vitrine/ProjectEventListener.java`
- `db/migration/V21__permissions_admin_colis_na_nga.sql`

**Backend Colis na Nga**

- `integration/ma/` : `MaAdminRelayFilter`, `MaCoreJwtDecoders`, `MaIntegrationProperties`, `MaSecurityConfig`,
  `MaController`, `MaReadService`, `MaEventListeners`, `OutboxEvent`, `OutboxEventRepository`, `OutboxWriter`, `OutboxPublisher`
- `config/SecurityConfig.java`, `config/DataInitializer.java` (compte de service `MA-WORKSPACE`)
- `entity/OrderTrackPoint.java`, `repository/OrderTrackPointRepository.java`, `service/OrderTrackRecorder.java`, `service/GeolocationService.java`
- `src/main/resources/application.properties` : bloc `ma.integration.*`
