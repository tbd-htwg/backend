# Trip planning backend

Spring Boot 3 **microservices** for a **trip planning** course project (HTWG Cloud Application Development): REST APIs for users, trips, **Google Places–backed stops and destinations**, accommodations, transports, **full-text trip search**, **profile and trip images** (Google Cloud Storage), **comments / likes** (Firestore), **external travel info** (weather, warnings, Viator tours, transport distance). Trip-service caches resolved places in a **`google_places`** table and enriches writes via external-info-service. Domain data lives in **PostgreSQL** with **Flyway** migrations in deployed environments (V10–V14 add Google Places schema); the SPA talks to **`/api/v2`** (Spring Data REST on trip-service) plus dedicated controllers for trip feed, accommodation/transport writes, search, social features, and uploads. **Auth and admin APIs** live on **platform-service**. Typical deployment: **GKE** (ms2) with Cloud SQL, Elasticsearch, Firestore, and GCS.

**Sibling app:** [../frontend/README.md](../frontend/README.md) (when this repo lives in a monorepo next to `frontend/`). **Infra overview:** [../infrastructure/ms2/docs/README.md](../infrastructure/ms2/docs/README.md) · **GKE architecture:** [../infrastructure/ms2/docs/STATE.md](../infrastructure/ms2/docs/STATE.md) (same). **Local Minikube:** [docs/gettingstarted/README.md](docs/gettingstarted/README.md). **GKE deploy:** [README-GKE.md](README-GKE.md). **Agent-oriented notes:** [AGENTS.md](AGENTS.md).

## Microservices (multi-module)

| Module | Port (local) | Role |
|--------|----------------|------|
| `tripplanning-trip-service` | 8080 | Trips, Google Places stops, GCS images, search, trip feed |
| `tripplanning-social-service` | 8081 | Firestore comments / likes |
| `tripplanning-external-info-service` | 8082 | Google Places search, weather, travel warnings, Viator tours, transport distance |
| `tripplanning-platform-service` | 8083 | Auth, admin API |
| `tripplanning-seed-job` | — | One-shot perf seed (PostgreSQL + Firestore; see [`tripplanning-seed-job/README.md`](tripplanning-seed-job/README.md)) |
| `tripplanning-common` | — | Shared clients and config |

**Local minikube:** [docs/gettingstarted/README.md](docs/gettingstarted/README.md) · **GKE architecture:** [../infrastructure/ms2/docs/STATE.md](../infrastructure/ms2/docs/STATE.md) · **GKE deploy:** [../infrastructure/ms2/docs/README.md](../infrastructure/ms2/docs/README.md)

**Paths:** Shell commands use the **backend project root** (`pom.xml` here). In a monorepo that folder is often named `backend/` under a top-level directory; if you opened **only** the backend repository, you are already at that root. Relative paths such as `../frontend/` assume the monorepo layout—adjust or ignore if your checkout differs.

## Prerequisites

- **Java 21**
- **Maven 3.9+**
- For **local** profile: optional **Firestore emulator** on port **9090** — start with **Firebase CLI** (`firebase emulators:start --only firestore`, uses [`firebase.json`](firebase.json)) or **`gcloud emulators firestore start --host-port=localhost:9090`** for comments and likes.
- For **default / production-like** runs: **PostgreSQL**, **Elasticsearch**, GCP/Firebase configuration as described below.

## Local development

### JVM-only (`dev.sh`)

All four services on the host without Kubernetes — Valkey, Firestore emulator, trip/social/external-info/platform on **:8080–:8083**. See [`scripts/README.md`](scripts/README.md) and env template [`scripts/dev.env`](scripts/dev.env).

```bash
cp scripts/dev.env docs/gettingstarted/.env   # or copy VITE_* lines to frontend/.env
./scripts/dev.sh start
```

Trip-service uses file-based **H2** and **Lucene** search (no Elasticsearch) under the `local` profile. For a single service only, see [README-GKE.md](README-GKE.md) Option B and [`application-local.yml`](tripplanning-trip-service/src/main/resources/application-local.yml).

**dev-login:** with **`local`**, **`POST /api/v2/auth/dev-login`** on platform-service accepts `{"email","name?"}` and returns an app JWT. **Do not enable `local` in production.**

**Frontend** (separate terminal): from `frontend/`, run `npm run dev` — see [../frontend/README.md](../frontend/README.md).

### Minikube (recommended)

Full stack on Kubernetes (in-cluster **PostgreSQL**, Valkey, OpenSearch, Firestore emulator, four microservices):

**[docs/gettingstarted/README.md](docs/gettingstarted/README.md)** · architecture: [docs/gettingstarted/STATE.md](docs/gettingstarted/STATE.md)

```bash
cp docs/gettingstarted/.env.example docs/gettingstarted/.env
# Optional: cp .env.local.example .env.local  (overrides; gitignored)
./scripts/local-dev.sh setup
./scripts/local-dev.sh port-forward
```

[`scripts/local-dev.sh`](scripts/local-dev.sh) loads env in order: **`docs/gettingstarted/.env`** → **`backend/.env.local`** (optional) → **`infrastructure/ms2/docs/gettingstarted/.env`** (optional, monorepo). See [`.env.example`](docs/gettingstarted/.env.example) for `JWT_SECRET`, **`GOOGLE_MAPS_API_KEY`** (Places API New), GCS bucket/signer, and optional `VIATOR_API_KEY`. Both `.env` files are gitignored.

Place search and trip write enrichment require a valid **`GOOGLE_MAPS_API_KEY`** — see [Places & external info](docs/gettingstarted/README.md#8-places--external-info-api-contract) in the getting-started guide.

**Frontend** (separate terminal): from `frontend/`, run `npm run dev:minikube` after port-forward — see [../frontend/README.md](../frontend/README.md).

## Production-like / default profile

Uses **`application.yml`**: **PostgreSQL** datasource (**`SPRING_DATASOURCE_*`**), **Flyway** on (`tripplanning-trip-service/src/main/resources/db/migration`, including V10–V14 Google Places schema), Hibernate **`ddl-auto: validate`**, Hibernate Search backend **Elasticsearch** (**`ELASTICSEARCH_HOSTS`**, optional auth/path).

Also configure:

- **`TRIPPLANNING_AUTH_JWT_SECRET`** — required; signs application JWTs (≥32 bytes).
- **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** — Firebase project for verifying Google ID tokens on **`POST /api/v2/auth/google`**. On Cloud Run, this is often aligned with **`GCP_PROJECT_ID`**.
- **`GCP_FIRESTORE_DATABASE_ID`** — Firestore database id (e.g. `(default)` or a named database).
- **`tripplanning.cors.allowed-origins`** / **`CORS_ALLOWED_ORIGINS`** — browser origins for the SPA.

Example:

```bash
mvn -pl tripplanning-trip-service spring-boot:run
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

Auth endpoints are served by **platform-service** (:8083 locally). Trip, social, and external-info **validate** the app JWT only.

| Method | Endpoint | Notes |
|--------|----------|--------|
| Google | **`POST /api/v2/auth/firebase`** | Body: `{ "credential": "<Firebase ID token>" }` → `{ tokenType, accessToken, user }` |
| Current user | **`GET /api/v2/auth/me`** | Requires `Authorization: Bearer <accessToken>` |
| Dev only | **`POST /api/v2/auth/dev-login`** | **`local` profile only**; body `{ "email", "name?" }` |

Other **`/api/v2/**`**: most **GET** requests are public; **GET** on **`/api/v2/users`**, **`/api/v2/users/search`** (and search subpaths), **`GET /api/v2/trips/*/liked-by-current-user`**, and **mutating** methods require a valid JWT. See [`SecurityConfig.java`](tripplanning-trip-service/src/main/java/com/tripplanning/api/config/SecurityConfig.java) for trip-service rules.

### Test bearer impersonation (non-prod only)

Set **`TRIPPLANNING_AUTH_TEST_BEARER_TOKEN`** to a shared secret on `develop` (and optionally `staging`) deployments to enable [`TestBearerImpersonationFilter`](tripplanning-trip-service/src/main/java/com/tripplanning/auth/TestBearerImpersonationFilter.java). When activated, any request that presents `Authorization: Bearer <that token>` together with `X-Act-As-User: <userId>` is authenticated as that user (no JWT verification, no expiry). If **`X-Act-As-User` is omitted**, the subject is **`0`** (bootstrap for seeding **`POST /users`** before any user row exists). Used by the seeder and Locust to write as many users from one shared secret. **Never set this on production**; if the env var is empty (default), the filter bean is not registered and behaviour is identical to before.

## Main HTTP surface

- **Spring Data REST** collections and item resources under **`/api/v2`** on trip-service (users, trips, trip locations, etc.), plus repository **search** endpoints where defined. Trips require **`destinationGooglePlaceId`**; trip stops use **`googlePlaceId`** (not `/api/v2/locations`, which was removed).
- **Trip feed:** **`GET /api/v2/trips/feed`**, **`/feed/by-user`**, **`/feed/liked-by`**, **`/{id}/detail`** (see [`TripFeedController`](tripplanning-trip-service/src/main/java/com/tripplanning/trip/read/TripFeedController.java)). With **`?mode=recommended`** (JWT required), the feed uses Elasticsearch/OpenSearch **More Like This** on destination, title, and short description, anchored on the user's liked trips—or, if they have none, their recent own trips—while excluding their likes and own posts, and falls back to the chronological feed when there are no anchors or no similar hits.
- **Accommodation / transport writes:** **`POST` / `PUT /api/v2/accommodations`** and **`/api/v2/transports`** (JWT required; SDR `save` disabled on those repositories). Bodies use Google place IDs — see [getting-started API contract](docs/gettingstarted/README.md#8-places--external-info-api-contract).
- **Trip search:** **`GET /api/search/...`** (see [`TripSearchController`](tripplanning-trip-service/src/main/java/com/tripplanning/search/TripSearchController.java)).
- **Social:** Firestore-backed **comments** and **likes** on social-service (`com.tripplanning.social`).
- **Platform** (platform-service): **`/api/v2/auth/**`**, **`/api/v2/admin/**`** — see _Authentication_ above.
- **External info** (external-info-service, routed at **`/api/v2/external/**`** via ingress): Google Places search (`/details/search`), stop details (`/stop-details`), accommodation tours (`/accommodation-details`), plus deprecated `/details` endpoints. Transport routes are computed in the **frontend** via Google Routes API. Trip-service calls **`/internal/location-pack`** for place enrichment on writes.

## Firestore `likes` document IDs

New like documents use a deterministic id **`{userId}_{tripId}`** so one read/delete can target the document without a field query. Older deployments may still hold legacy random ids; APIs that query by `userId` and `tripId` can still find those rows.

## Seed data

**Recommended (performance dataset):** after Minikube setup and GCS sample images:

```bash
./scripts/local-dev.sh sync-sample-images
./scripts/local-dev.sh seed-job
```

See [`tripplanning-seed-job/README.md`](tripplanning-seed-job/README.md). Writes `performance/seeding_example/perf_seed_manifest.json` for Locust.

**Small smoke dataset** via REST API (users, trips, stops with `googlePlaceId`, accommodations, transports, likes, comments). See [getting-started Places section](docs/gettingstarted/README.md#8-places--external-info-api-contract) for required write payloads:

```bash
cd ../performance/seeding_example
python3 seed_example_data.py --help
```

(`../performance` is correct when `performance/` sits next to this backend folder in the monorepo; adjust if your tree differs.)

With a local API and `local` profile, options such as **`--fetch-dev-login`** can obtain a token automatically. For deployed dev environments use the unified test bearer (see _Test bearer impersonation_ above and the [seeder README](../performance/seeding_example/README.md)) so likes and comments are attributed to many users from a single shared secret.

## Project layout

| Module | Packages (high level) |
|--------|------------------------|
| `tripplanning-trip-service` | `user`, `trip`, `tripLocation`, `place`, `accommodation`, `transport`, `search`, `images`, `api.config`, `api.projections` |
| `tripplanning-social-service` | `social` (Firestore comments / likes) |
| `tripplanning-external-info-service` | `externalinfo` (Google Places, weather, warnings, Viator, transport distance) |
| `tripplanning-platform-service` | `platform` (auth, admin API) |
| `tripplanning-common` | shared HTTP clients, JWT decoder config |

**Local JVM artifacts:** `./temp/db/` and `./temp/search/` hold H2 and Lucene data when running trip-service with `local` profile on the host (gitignored; safe to delete). Minikube uses in-pod storage instead.

## Run tests

```bash
mvn test
```

## Docker

Build per service (from backend root):

```bash
docker build --build-arg SERVICE=trip -t tripplanning-trip-service:local .
docker build --build-arg SERVICE=social -t tripplanning-social-service:local .
docker build --build-arg SERVICE=external-info -t tripplanning-external-info-service:local .
docker build --build-arg SERVICE=platform -t tripplanning-platform-service:local .
```

For Minikube, [`scripts/local-dev.sh`](scripts/local-dev.sh) builds `:local` images inside the minikube Docker daemon.

Runtime images expect the same environment variables as a non-local Spring profile (Postgres, secrets, Elasticsearch, GCP, etc.). The Dockerfile uses a **glibc** base image for Firestore/gRPC compatibility.
