# MA Core : backend de MA Workspace (Mindset Alliance)

Backend central de **MA Workspace**, la console interne de **Mindset Alliance**.
Il gère les comptes des agents, les rôles et permissions, la billetterie (tickets),
les RH, la finance, les documents, les rapports PDF, l'assistant IA, et la
**supervision et l'administration de Colis na Nga**.

| | |
|---|---|
| Langage | Java 21 |
| Framework | Spring Boot 3.3.5 (Web, Security, Data JPA, Validation, Mail, Kafka) |
| Base de données | MySQL 8 (Flyway, migrations `V1` à `V22`) |
| Authentification | JWT RS256 signés par MA Core, double facteur (code e-mail) |
| Port | `8081` |
| Frontend associé | [Frontend_MindSetAlliance](https://github.com/sharo8/Frontend_MindSetAlliance) |

> Liaison avec Colis na Nga : tout est expliqué pas à pas dans
> **[docs/INTEGRATION-COLIS-NA-NGA.md](docs/INTEGRATION-COLIS-NA-NGA.md)**.

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Structure du code](#2-structure-du-code)
3. [Modules et API](#3-modules-et-api)
4. [Base de données et migrations](#4-base-de-données-et-migrations)
5. [Sécurité et authentification](#5-sécurité-et-authentification)
6. [Variables d'environnement](#6-variables-denvironnement)
7. [Lancer le projet en local](#7-lancer-le-projet-en-local)
8. [Tests](#8-tests)
9. [Docker et production](#9-docker-et-production)
10. [Dépannage](#10-dépannage)
11. [Règles de contribution](#11-règles-de-contribution)

---

## 1. Vue d'ensemble

```
                    ┌──────────────────────────┐
  Navigateur  ───►  │  Frontend MA Workspace   │  React + Vite (port 5173)
                    └────────────┬─────────────┘
                                 │  /api/**  (JWT MA)
                    ┌────────────▼─────────────┐
                    │   MA Core (ce dépôt)     │  Spring Boot (port 8081)
                    │   MySQL ma-mysql :3308   │
                    └───┬──────────────────┬───┘
       lecture seule    │                  │  écritures (relais signé)
       (ma_reader)      │                  │  en-tête X-MA-Relay
                    ┌───▼────────┐   ┌─────▼──────────────────┐
                    │ Base CNN   │   │ API admin Colis na Nga │
                    │ colisnanga │   │ Cloud Run colis-backend│
                    │ Cloud SQL  │   │ /api/admin/**          │
                    └────────────┘   └────────────────────────┘
```

Principes :

- **Aucune donnée fictive.** Les données de démonstration ont été retirées (`V20`, `V22`).
  Un indicateur qui ne peut pas être lu s'affiche comme « indisponible », jamais avec une valeur inventée.
- **Colis na Nga est lu en lecture seule** directement dans sa base (utilisateur MySQL `ma_reader`, droit `SELECT` uniquement).
- **Toute modification Colis na Nga passe par l'API admin de Colis na Nga** (relais signé),
  pour que les règles métier, notifications et journaux d'audit de l'application s'appliquent.
- Chaque action est contrôlée par une **permission MA** (VIEW, CREATE, UPDATE, DELETE) et
  journalisée dans l'audit MA.

---

## 2. Structure du code

```
backend/
├── Dockerfile
├── pom.xml
├── .env.example                 modèle de configuration locale (copier en .env, jamais commité)
├── docs/
│   └── INTEGRATION-COLIS-NA-NGA.md
├── data/                        clé RSA locale générée au démarrage (ignorée par Git)
└── src/main/
    ├── java/com/mindsetalliance/core/
    │   ├── CoreApplication.java
    │   ├── assistant/      assistant IA (Groq), conversations, retours
    │   ├── audit/          journal d'audit (qui a fait quoi, quand)
    │   ├── auth/           connexion, 2FA, jetons, mots de passe, JWKS
    │   ├── cnn/            Colis na Nga : lecture seule + relais admin
    │   │   ├── admin/      CnnAdminRelayController, CnnAdminScope
    │   │   └── synthese/   tableau de bord, listes, carte, itinéraires, statistiques
    │   ├── common/         erreurs, utilitaires, rôles JWT
    │   ├── config/         sécurité, CORS, clés RSA, chiffrement, .env local
    │   ├── documents/      documents et pièces jointes
    │   ├── finance/        écritures, budgets, taux de change
    │   ├── hr/             agents, dossiers, présences, paie
    │   ├── iam/            sociétés, départements, rôles, permissions, accès
    │   ├── notifications/  notifications des agents
    │   ├── ops/            opérations et supervision
    │   ├── reports/        rapports PDF (accès, organisation, statistiques…)
    │   ├── tickets/        billetterie interne (CDC)
    │   └── vitrine/        indicateurs publics des projets
    └── resources/
        ├── application.yml          configuration commune
        ├── application-local.yml    profil local (par défaut)
        ├── application-prod.yml     profil production
        ├── branding/                logos utilisés dans les PDF
        └── db/migration/            scripts Flyway V1 à V22
```

---

## 3. Modules et API

Toutes les routes sont préfixées par `/api`. Sauf mention contraire, elles exigent
un jeton MA valide (`Authorization: Bearer <jwt>`).

| Préfixe | Module | Contenu principal |
|---|---|---|
| `/api/auth` | auth | `login`, `2fa/verify`, `refresh`, `logout`, `me`, mots de passe, `/.well-known/jwks.json` (public) |
| `/api/iam` | iam | sociétés, départements, agents, rôles, matrice de permissions, dérogations, historique d'accès |
| `/api/roles`, `/api/permissions` | iam | rôles et permissions, aperçu de la matrice |
| `/api/agents` | hr | fiches agents, photos |
| `/api/hr` | hr | dossiers, présences, calcul et PDF de paie |
| `/api/finance` | finance | écritures (créer, valider, annuler), budgets, taux |
| `/api/tickets` | tickets | tickets, attribution, commentaires, statuts, pièces jointes, création depuis un incident |
| `/api/documents` | documents | documents de l'entreprise |
| `/api/notifications` | notifications | liste, marquer comme lu |
| `/api/dashboard` | dashboards | `direction`, `finance`, `rh`, `cnn`, `accueil` |
| `/api/ops` | ops | registres opérationnels par module |
| `/api/audit` | audit | journal d'audit, export |
| `/api/admin/rapports` | reports | PDF : `acces.pdf`, `organisation.pdf`, `permissions.pdf`, `utilisateurs.pdf`, `statistiques.pdf` |
| `/api/admin/assistant` | assistant | `chat`, conversations, retours |
| `/api/vitrine` | vitrine | indicateurs des projets (alimentés par Kafka) |
| `/api/cnn` | cnn | `synthese`, `status`, `admin-relay/status`, relais `admin/**` |
| `/api/cnn/lecture` | cnn | listes et carte Colis na Nga (lecture seule) |
| `/api/internal/cnn` | cnn | `POST /incidents` : réception d'incidents envoyés par Colis na Nga (clé interne) |

### Module Colis na Nga (`cnn/`)

| Classe | Rôle |
|---|---|
| `CnnProperties` | configuration `ma.cnn.*` |
| `CnnDataSourceConfig` | pool JDBC dédié à la base Colis na Nga (8 connexions, 3 gardées ouvertes, keepalive 60 s) |
| `CnnGrantVerifier` | au démarrage, `SHOW GRANTS` : refuse de se dire « lecture seule vérifiée » si un droit autre que `SELECT`/`SHOW VIEW`/`USAGE` existe |
| `CnnReadOnlyClient`, `CnnSql`, `CnnWriteRejectedException` | garde-fou : toute requête autre que lecture est rejetée |
| `CnnParallel` | lectures en parallèle (6 fils, 25 s max par bloc) |
| `CnnWarmup` | au démarrage prépare base, API, synthèse et statistiques ; toutes les 4 min réveille la base et Cloud Run |
| `CnnSourceHealthIndicator`, `CnnSourceStatus` | état de la source Colis na Nga |
| `synthese/CnnSyntheseService` | tableau de bord (`GET /api/cnn/synthese`, cache 10 s) |
| `synthese/CnnLectureService` + `CnnLectureController` | listes, carte, itinéraires, clients, crédits |
| `synthese/CnnStatistiquesService` | statistiques par période (cache 60 s) |
| `synthese/CnnExchangeRates` | taux USD → CDF (open.er-api.com) |
| `admin/CnnAdminRelayController` | relais `/api/cnn/admin/**` → `/api/admin/**` de Colis na Nga |
| `admin/CnnAdminScope` | correspondance ressource Colis na Nga → permission MA |

Routes de lecture (`/api/cnn/lecture/...`) :

| Route | Contenu |
|---|---|
| `orders`, `orders/{id}/itineraire` | commandes ; itinéraire complet (arrêts, chronologie, tracé GPS) |
| `clients`, `clients/{id}/orders` | clients ponctuels et abonnés, avec activité ; commandes d'un client |
| `agents`, `motards`, `associations`, `associations/{id}`, `associations/{id}/motards` | partenaires |
| `credits` | crédits des motards |
| `carte`, `carte/commandes` | positions en direct ; commandes à tracer |
| `statistiques?jours=7\|30\|90\|365` | statistiques avec comparaison à la période précédente |

---

## 4. Base de données et migrations

- Base MA : `mindsetalliance_core` (MySQL, conteneur Docker `ma-mysql`, port hôte `3308`).
- Hibernate en `validate` : **le schéma est uniquement géré par Flyway**. Ne jamais modifier une migration déjà appliquée : créer la suivante (`V23__...sql`).

| Version | Contenu |
|---|---|
| V1 | schéma initial |
| V2 | données de démonstration (retirées ensuite par V20 et V22) |
| V3, V4 | corrections de colonnes, champs du profil agent |
| V5, V6 | matrice RBAC, rôles et permissions du CDC |
| V7, V8 | registres opérationnels, tableau d'accueil |
| V9 | administration réelle |
| V10 à V12 | billetterie (catégories, CDC, type « autre », historique) |
| V13 | ville de référence des projets |
| V14 | dérogations de permissions par agent |
| V15, V16 | rôles par défaut des départements |
| V17, V18 | libellés et actions des permissions (VIEW/CREATE/UPDATE/DELETE) |
| V19 | conversations de l'assistant |
| V20 | suppression des opérations Colis na Nga de démonstration |
| V21 | permissions d'administration Colis na Nga |
| V22 | purge de toutes les données de démonstration |

Permissions ajoutées par V21 (module « Colis na Nga ») :

`VIEW_CNN_DASHBOARD`, `MANAGE_CNN_ORDERS`, `MANAGE_CNN_AGENTS`, `MANAGE_CNN_MOTARDS`,
`MANAGE_CNN_ASSOCIATIONS`, `MANAGE_CNN_CLIENTS`, `MANAGE_CNN_SUBSCRIPTIONS`,
`MANAGE_CNN_PAYMENTS`, `MANAGE_CNN_SETTINGS`, `MANAGE_CNN_ADMINS`.
Les rôles `ADMIN_SYSTEME` et `DIRECTION` les reçoivent toutes.

Si Flyway signale une somme de contrôle différente en local : le profil `local` active
`ma.flyway.repair-on-migrate`, qui répare l'historique au démarrage. En production, il est désactivé.

---

## 5. Sécurité et authentification

1. `POST /api/auth/login` (e-mail + mot de passe, 5 tentatives par minute).
2. Un code à 6 chiffres est envoyé par e-mail ; `POST /api/auth/2fa/verify` le valide.
3. MA Core renvoie un **jeton d'accès RS256 (15 min)** et un jeton de rafraîchissement (7 jours).
4. La clé publique est exposée sur `GET /api/auth/.well-known/jwks.json` (c'est elle que Colis na Nga utilise pour vérifier le relais).

Points importants :

- **Clé de signature RSA** : fichier `./data/rsa.jwk` (variable `MA_AUTH_RSA_KEYSTORE`).
  Il est créé automatiquement s'il manque. **Il contient la clé privée : il ne doit jamais être versionné** (`data/` est dans `.gitignore`).
  Si la clé change, tous les agents sont déconnectés et il faut recopier la nouvelle clé publique dans `MA_CORE_JWK` côté Colis na Nga.
- **Code 2FA de démonstration** : `MA_AUTH_DEMO_TOTP=true` accepte `000000` (profil local uniquement, pratique sans SMTP). **Toujours `false` en production**, avec un SMTP configuré.
- **Clé interne** : les routes `/api/internal/**` exigent l'en-tête `X-MA-Internal-Key` égal à `MA_INTERNAL_API_KEY` (ouvert en profil local).
- **Chiffrement** : `MA_AES_KEY` (clé AES 256 bits en Base64) chiffre les données sensibles. La valeur du profil local est publique : en générer une autre pour la production (`openssl rand -base64 32`).
- **CORS** : `MA_CORS_ORIGINS` (en local, tous les ports de `localhost`).
- En production : HTTPS obligatoire, détails d'erreurs masqués, Swagger désactivé.

---

## 6. Variables d'environnement

En local, elles peuvent être mises dans un fichier `.env` à la racine du backend
(lu par `LocalEnvFile`, ignoré par Git). Voir `.env.example`.
**Ne jamais écrire un mot de passe ou une clé dans le code, dans un README ou dans un commit.**

### Application et base MA

| Variable | Défaut | Rôle |
|---|---|---|
| `SERVER_PORT` | `8081` | port HTTP |
| `SPRING_PROFILES_ACTIVE` | `local` | `local` ou `prod` |
| `DATABASE_URL` | MySQL `localhost:3308/mindsetalliance_core` | base MA |
| `DATABASE_USERNAME` | `root` | utilisateur MySQL MA |
| `DATABASE_PASSWORD` | (dev local) | **à toujours définir soi-même** |
| `MA_CORS_ORIGINS` | `http://localhost:8080` | origines autorisées (obligatoire en prod) |

### Authentification et sécurité

| Variable | Rôle |
|---|---|
| `MA_AUTH_ISSUER` | émetteur des jetons (défaut `mindset-alliance-core`) |
| `MA_AUTH_RSA_KEYSTORE` | chemin de la clé RSA (défaut `./data/rsa.jwk`) |
| `MA_AUTH_DEMO_TOTP` | `true` = code `000000` accepté (local seulement) |
| `MA_AES_KEY` | clé AES Base64 |
| `MA_INTERNAL_API_KEY` | clé exigée sur `/api/internal/**` |

### E-mail (SMTP)

`MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`,
`MAIL_FROM_ADDRESS`, `MAIL_FRONTEND_BASE_URL` (adresse du frontend dans les liens envoyés), `MAIL_TEST_TO`.

### Assistant IA

`GROQ_API_KEY` (vide = assistant non configuré), `GROQ_BASE_URL`, `GROQ_MODEL`.

### Kafka

`KAFKA_BOOTSTRAP_SERVERS` (défaut `localhost:9092`), `MA_KAFKA_ENABLED` (désactivé en profil local).
Sujets écoutés : `colisnanga.courses.events`, `colisnanga.coursiers.events`, `mdr.contenus.events` ; publié : `core.tickets.events`.

### Colis na Nga

| Variable | Défaut | Rôle |
|---|---|---|
| `MA_CNN_MODE` | `db` | lecture directe de la base Colis na Nga |
| `MA_CNN_DB_URL` | vide | JDBC de la base `colisnanga` (sans URL : écrans Colis na Nga « indisponibles ») |
| `MA_CNN_DB_USERNAME` | vide | `ma_reader` |
| `MA_CNN_DB_PASSWORD` | vide | mot de passe de `ma_reader` |
| `MA_CNN_CACHE_SECONDS` | `10` (prod `30`) | cache du tableau de bord |
| `MA_CNN_STORAGE_ZONE` | `UTC` | fuseau des dates enregistrées par Colis na Nga |
| `MA_CNN_API_BASE_URL` | URL Cloud Run de `colis-backend` | API utilisée par le relais |
| `MA_CNN_API_TIMEOUT_SECONDS` | `20` | délai du relais |

---

## 7. Lancer le projet en local

### Prérequis

- Java 21 (`/usr/lib/jvm/java-21-openjdk-amd64` sous Linux) et Maven 3.9+
- Docker (pour MySQL)
- Pour Colis na Nga : `gcloud` connecté au projet `colis-na-ngai` et le binaire `cloud-sql-proxy`

### Étape 1 : MySQL de MA

Première fois :

```bash
docker run -d --name ma-mysql -p 3308:3306 \
  -e MYSQL_ROOT_PASSWORD="$(read -s -p 'Mot de passe root MySQL : ' p; echo "$p")" \
  mysql:8.0
```

Les fois suivantes :

```bash
docker start ma-mysql
```

La base `mindsetalliance_core` est créée automatiquement, et Flyway applique les migrations au démarrage.

### Étape 2 : accès à la base Colis na Nga (facultatif mais nécessaire pour les écrans Colis na Nga)

Dans un terminal séparé, laissé ouvert :

```bash
gcloud auth application-default login     # une seule fois
~/cloud-sql-proxy --port 3310 colis-na-ngai:europe-west1:colis-db
```

### Étape 3 : démarrer le backend

```bash
cd backend
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export DATABASE_USERNAME=root
read -s -p "Mot de passe MySQL MA : " DATABASE_PASSWORD; echo; export DATABASE_PASSWORD
export MA_AUTH_DEMO_TOTP=true
export MA_CNN_MODE=db
export MA_CNN_DB_URL='jdbc:mysql://127.0.0.1:3310/colisnanga?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Kinshasa'
export MA_CNN_DB_USERNAME=ma_reader
read -s -p "Mot de passe ma_reader : " MA_CNN_DB_PASSWORD; echo; export MA_CNN_DB_PASSWORD
mvn spring-boot:run
```

`read -s` permet de taper les mots de passe sans qu'ils apparaissent à l'écran ni dans l'historique du terminal.

Vérifications :

- `http://localhost:8081/actuator/health` → `UP`
- `http://localhost:8081/swagger-ui.html` → documentation de l'API (profil local)
- Dans les journaux : « Relais Colis na Nga opérationnel. » quand la liaison d'écriture fonctionne.

### Étape 4 : le frontend

Voir le README du dépôt frontend (`npm install` puis `npm run dev`, ouvert sur `http://localhost:5173`).

---

## 8. Tests

```bash
mvn test        # tests unitaires
mvn verify      # + tests d'intégration (*IT.java)
```

---

## 9. Docker et production

```bash
docker build -t ma-core .
docker run -p 8081:8081 --env-file .env -e SPRING_PROFILES_ACTIVE=prod ma-core
```

Liste de contrôle production :

- `SPRING_PROFILES_ACTIVE=prod`, `MA_CORS_ORIGINS` = domaine du frontend.
- `MA_AUTH_DEMO_TOTP=false` et SMTP configuré.
- `MA_AES_KEY`, `MA_INTERNAL_API_KEY`, `DATABASE_PASSWORD` définis comme secrets (Secret Manager, pas en clair).
- Clé RSA stockée sur un volume persistant ou un secret, **jamais dans l'image ni dans Git**.
- Côté Colis na Nga, `MA_CORE_JWKS_URL=https://<domaine MA>/api/auth/.well-known/jwks.json`.

---

## 10. Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| Écrans Colis na Nga « indisponible » | `MA_CNN_DB_URL` vide ou proxy Cloud SQL arrêté | relancer `cloud-sql-proxy --port 3310 …` |
| Proxy : `403` / `NOT_AUTHORIZED` | session `gcloud` expirée | `gcloud auth application-default login` |
| Boutons de modification Colis na Nga absents | relais non prêt | voir `GET /api/cnn/admin-relay/status` et [l'intégration](docs/INTEGRATION-COLIS-NA-NGA.md#9-dépannage) |
| `Unknown column ...` sur une table Colis na Nga | nom de clé étrangère différent | `CnnLectureService.fk()` lit le vrai nom dans `information_schema` ; s'en servir pour toute nouvelle requête |
| Flyway : checksum mismatch | migration modifiée après application | ne pas modifier, créer une nouvelle version |
| Code 2FA jamais reçu | SMTP non configuré | configurer `MAIL_*` ou, en local, `MA_AUTH_DEMO_TOTP=true` |

---

## 11. Règles de contribution

- Les noms **Mindset Alliance**, **MA Workspace**, **Colis na Nga** et **Mindset du Royaume** ne se traduisent jamais.
- Aucune donnée inventée dans le code ou les migrations.
- Jamais d'écriture directe dans la base Colis na Nga : utiliser le relais.
- Toute nouvelle ressource du relais doit être déclarée dans `CnnAdminScope` avec sa permission.
- Aucun secret dans Git : `.env`, `data/`, `application-secrets.yml` sont ignorés.
- Branche principale : `main`. Messages de commit en français, clairs et courts.
