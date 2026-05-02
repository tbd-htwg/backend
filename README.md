# Trip planning backend

Spring Boot 3 service for a **trip planning** course project (HTWG Cloud Application Development): REST API for users, trips, locations, accommodations, transports, **full-text trip search**, **profile and trip images** (Google Cloud Storage), and **comments / likes** stored in **Firestore**. Domain data lives in **PostgreSQL** with **Flyway** migrations in deployed environments; the SPA talks to **`/api/v2`** (Spring Data REST) plus dedicated controllers for auth, search, social features, and uploads. Typical deployment: **Cloud Run** with GCP-managed Postgres, Elasticsearch, Firestore, and GCS.

**Sibling app:** [../frontend/README.md](../frontend/README.md) (when this repo lives in a monorepo next to `frontend/`). **Infra overview:** [../infrastructure/README.md](../infrastructure/README.md) (same). **Agent-oriented notes:** [AGENTS.md](AGENTS.md).

**Paths:** Shell commands use the **backend project root** (`pom.xml` here). In a monorepo that folder is often named `backend/` under a top-level directory; if you opened **only** the backend repository, you are already at that root. Relative paths such as `../frontend/` assume the monorepo layout—adjust or ignore if your checkout differs.

## Prerequisites

- **Java 21**
- **Maven 3.9+**
- For **local** profile: optional **Firestore emulator** on port **9090** — start with **Firebase CLI** (`firebase emulators:start --only firestore`, uses [`firebase.json`](firebase.json)) or **`gcloud emulators firestore start --host-port=localhost:9090`** for comments and likes.
- For **default / production-like** runs: **PostgreSQL**, **Elasticsearch**, GCP/Firebase configuration as described below.

## Local development (`local` profile)

Use this for everyday work: file-based **H2**, **Hibernate Search** with **Lucene** (no Elasticsearch required), and a dev JWT secret from [`application-local.yml`](src/main/resources/application-local.yml).

### Firestore emulator

Listen on **`localhost:9090`** so it matches `spring.cloud.gcp.firestore.host-port` in [`application-local.yml`](src/main/resources/application-local.yml).

**Firebase CLI** comes from the [`firebase-tools`](https://www.npmjs.com/package/firebase-tools) npm package (e.g. `npm install -g firebase-tools`). **Or** use **Google Cloud CLI** (`gcloud`). Run **one** of the following:

```bash
# Firebase CLI — uses firebase.json; run from this project root
firebase emulators:start --only firestore

# Or: Google Cloud CLI — no firebase.json required
gcloud emulators firestore start --host-port=localhost:9090
```

### Run the API

From this project root:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Optional: set **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** if you use Google sign-in against a specific Firebase project. Override **`TRIPPLANNING_AUTH_JWT_SECRET`** (≥32 UTF-8 bytes) if you must not rely on the file default.

**What `local` does**

| Concern | Behavior |
|--------|----------|
| Database | H2 file: `./temp/db/tripplanning-dev` |
| Schema | **Flyway disabled**; JPA **`ddl-auto: create-drop`** (fresh schema each run) |
| Search | Lucene indexes under **`./temp/search`** |
| Firestore | Emulator enabled; database id defaults to **`(default)`** unless **`GCP_FIRESTORE_DATABASE_ID`** is set |

API base: **`http://localhost:8080/api/v2`**. Start the [frontend dev server](../frontend/README.md) (usually `http://localhost:5173`).

### Google sign-in locally

Use the same **`local`** profile and set **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** to your Firebase project if it differs from the default in `application.yml`. The frontend must use an OAuth origin allowed in Firebase Authentication.

### Dev login (no Google)

With **`local`**, **`POST /api/v2/auth/dev-login`** is registered. JSON body: `email` (required), optional `name`. Response matches Google login (`tokenType`, `accessToken`, `user`). **Do not enable `local` in production.**

## Production-like / default profile

Uses **`application.yml`**: **PostgreSQL** datasource (**`SPRING_DATASOURCE_*`**), **Flyway** on (`src/main/resources/db/migration`), Hibernate **`ddl-auto: validate`**, Hibernate Search backend **Elasticsearch** (**`ELASTICSEARCH_HOSTS`**, optional auth/path).

Also configure:

- **`TRIPPLANNING_AUTH_JWT_SECRET`** — required; signs application JWTs (≥32 bytes).
- **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** — Firebase project for verifying Google ID tokens on **`POST /api/v2/auth/google`**. On Cloud Run, this is often aligned with **`GCP_PROJECT_ID`**.
- **`GCP_FIRESTORE_DATABASE_ID`** — Firestore database id (e.g. `(default)` or a named database).
- **`tripplanning.cors.allowed-origins`** / **`CORS_ALLOWED_ORIGINS`** — browser origins for the SPA.

Example:

```bash
mvn spring-boot:run
```

## API reference (OpenAPI / Swagger)

- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

To refresh the checked-in spec for the frontend (with the server running), from this project root in a **monorepo** where `frontend` is a sibling folder:

```bash
curl -sS http://localhost:8080/v3/api-docs -o ../frontend/doc/swagger_v2.json
```

Otherwise set `-o` to the path of `doc/swagger_v2.json` in your frontend checkout.

## Authentication

| Method | Endpoint | Notes |
|--------|----------|--------|
| Google | **`POST /api/v2/auth/google`** | Body: `{ "credential": "<Firebase ID token>" }` → `{ tokenType, accessToken, user }` |
| Current user | **`GET /api/v2/auth/me`** | Requires `Authorization: Bearer <accessToken>` |
| Dev only | **`POST /api/v2/auth/dev-login`** | **`local` profile only**; body `{ "email", "name?" }` |

Other **`/api/v2/**`**: most **GET** requests are public; **GET** on **`/api/v2/users`**, **`/api/v2/users/search`** (and search subpaths), **`GET /api/v2/trips/*/liked-by-current-user`**, and **mutating** methods require a valid JWT. See [`SecurityConfig.java`](src/main/java/com/tripplanning/api/config/SecurityConfig.java) for the exact rules.

### Test bearer impersonation (non-prod only)

Set **`TRIPPLANNING_AUTH_TEST_BEARER_TOKEN`** to a shared secret on `develop` (and optionally `staging`) deployments to enable [`TestBearerImpersonationFilter`](src/main/java/com/tripplanning/auth/TestBearerImpersonationFilter.java). When activated, any request that presents `Authorization: Bearer <that token>` together with `X-Act-As-User: <userId>` is authenticated as that user (no JWT verification, no expiry). If **`X-Act-As-User` is omitted**, the subject is **`0`** (bootstrap for seeding **`POST /users`** before any user row exists). Used by the seeder and Locust to write as many users from one shared secret. **Never set this on production**; if the env var is empty (default), the filter bean is not registered and behaviour is identical to before.

## Main HTTP surface

- **Spring Data REST** collections and item resources under **`/api/v2`** (users, trips, trip locations, etc.), plus repository **search** endpoints where defined.
- **Trip search:** **`GET /api/search/...`** (see [`TripSearchController`](src/main/java/com/tripplanning/search/TripSearchController.java)).
- **Social:** Firestore-backed **comments** and **likes** via dedicated controllers in **`com.tripplanning.social`** (HAL-style JSON compatible with the SPA).

## Firestore `likes` document IDs

New like documents use a deterministic id **`{userId}_{tripId}`** so one read/delete can target the document without a field query. Older deployments may still hold legacy random ids; APIs that query by `userId` and `tripId` can still find those rows.

## Seed example data

Realistic demo data (users, trips, locations, accommodations, transports, likes, comments) via the REST API:

```bash
cd ../performance/seeding_example
python3 seed_example_data.py --help
```

(`../performance` is correct when `performance/` sits next to this backend folder in the monorepo; adjust if your tree differs.)

With a local API and `local` profile, options such as **`--fetch-dev-login`** can obtain a token automatically. For deployed dev environments use the unified test bearer (see _Test bearer impersonation_ above and the [seeder README](../performance/seeding_example/README.md)) so likes and comments are attributed to many users from a single shared secret.

## Project layout (`com.tripplanning`)

| Package | Role |
|---------|------|
| `auth` | Google and dev login, JWT issuance |
| `user`, `trip`, `tripLocation`, `location`, `accommodation`, `transport` | JPA entities and Spring Data REST |
| `social` | Firestore documents and REST for comments / likes |
| `search` | Hibernate Search indexing and search API |
| `images` | GCS-backed uploads |
| `api.config` | Security, OpenAPI |
| `api.projections` | Stable JSON projections for lists and detail |
| `config` | Cross-cutting Spring configuration |

## Run tests

```bash
mvn test
```

## Docker

Build:

```bash
docker build -t trip-backend:local .
```

Run:

```bash
docker run --rm -p 8080:8080 trip-backend:local
```

The runtime image expects the same environment variables as a non-local Spring profile (Postgres, secrets, Elasticsearch, GCP, etc.). The Dockerfile uses a **glibc** base image for Firestore/gRPC compatibility.
