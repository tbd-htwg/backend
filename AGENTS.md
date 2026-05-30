# Agent notes — trip planning backend

Concise context for AI assistants and contributors working in this directory. For full runbooks, see [README.md](README.md) and [docs/gettingstarted/README.md](docs/gettingstarted/README.md).

**Working directory:** Commands below assume the **root of the backend project** (where `pom.xml` lives). In a **monorepo** that is often `…/backend/` under a top-level folder; if you cloned **only** the backend repository, your shell is already that root—**do not** add an extra `backend/` prefix. The same idea applies to paths such as `../frontend/…`: they only work when the **frontend** tree exists next to **this** tree (typical monorepo layout). If your checkout does not include the frontend, skip cross-repo file operations or point to your actual path.

## What this is

Maven **multi-module** Spring Boot 3 backend:

| Module | Port | Role |
|--------|------|------|
| `tripplanning-trip-service` | 8080 | Trips, users, Google Places (`place`), trip stops (`tripLocation`), auth, GCS images, search |
| `tripplanning-social-service` | 8081 | Firestore comments / likes |
| `tripplanning-external-info-service` | 8082 | Google Places search, Google Routes transport distance, weather, warnings, Viator tours |
| `tripplanning-seed-job` | — | One-shot perf seed (PostgreSQL + Firestore; `./scripts/local-dev.sh seed-job`) |

After **any** perf seed (`local-dev.sh seed-job` or `gke-seed-job.sh`), run **`./scripts/reset-search-index.sh`** if search was not reset automatically. Seed inserts via JDBC bypass Hibernate Search; trip-service skips mass reindex when OpenSearch doc counts already match PostgreSQL (stale index content). Both seed scripts call `reset-search-index.sh` by default; use `--skip-search-reset` only when debugging.
| `tripplanning-common` | — | Shared clients and auth config |

> Ignore repo-root `external-info-service/` — the supported module is `tripplanning-external-info-service`.

**Spring Data REST** on trip-service exposes domain resources under **`/api/v2`**. **OpenAPI 3** JSON: **`/v3/api-docs`** (public). Swagger UI: **`/swagger-ui/index.html`**.

**Recommended local stack:** Minikube via [`scripts/local-dev.sh`](scripts/local-dev.sh) — see [docs/gettingstarted/README.md](docs/gettingstarted/README.md). Default Minikube profile for trip-service: **`local,k8s,postgres`** (in-cluster PostgreSQL + Flyway), not file H2.

## Run locally (`local` profile, JVM-only)

See [README-GKE.md](README-GKE.md) Option B for all three services. Trip-service only:

```bash
gcloud emulators firestore start --host-port=localhost:9090
SPRING_PROFILES_ACTIVE=local mvn -pl tripplanning-trip-service spring-boot:run
```

- **H2** file DB: `./temp/db/tripplanning-dev` (gitignored; recreated on run); **Flyway off**; JPA **`create-drop`** each run.
- **Hibernate Search** uses the **Lucene** backend; indexes under **`./temp/search`** (gitignored).
- **Minikube** uses in-cluster **PostgreSQL** and **OpenSearch** — not the host `temp/` directory.
- **Firestore** for social: emulator on **`localhost:9090`** (JVM path) or in-cluster `firestore-emulator:8080` (k8s path).
- Auth: override with **`TRIPPLANNING_AUTH_JWT_SECRET`** (≥32 bytes); **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** for Google token verification.

Default trip-service: **`http://localhost:8080`**.

## Architecture (where things live)

| Area | Module / package |
|------|------------------|
| Core domain (JPA + REST) | `tripplanning-trip-service` — `user`, `trip`, `tripLocation`, `place`, `accommodation`, `transport` |
| Auth (Firebase ID token → app JWT) | `tripplanning-trip-service` — `com.tripplanning.auth` (`POST /api/v2/auth/firebase`) |
| Social (Firestore) | `tripplanning-social-service` — `com.tripplanning.social` |
| Full-text search | `tripplanning-trip-service` — `com.tripplanning.search` — **`/api/search/**`** |
| Images (GCS) | `tripplanning-trip-service` — `com.tripplanning.images` |
| External APIs | `tripplanning-external-info-service` — `com.tripplanning.externalinfo` |
| Security, OpenAPI | `tripplanning-trip-service` — `com.tripplanning.api.config` |
| JSON projections | `tripplanning-trip-service` — `com.tripplanning.api.projections` |

Production-like runs use **PostgreSQL**, **Flyway** in `tripplanning-trip-service/src/main/resources/db/migration` (V10–V14 Google Places), **OpenSearch** (Hibernate Search Elasticsearch-protocol backend) for full-text search. **Not** the same as JVM-only `local`.

## Conventions

- Keep new code aligned with existing packages and Spring patterns in the correct module.
- **Do not** turn Flyway back on for JVM-only `local` without an intentional workflow change (Postgres-flavored migrations do not match ad-hoc H2).
- Trip HTTP security: [`SecurityConfig.java`](tripplanning-trip-service/src/main/java/com/tripplanning/api/config/SecurityConfig.java).

## Firestore like document IDs

New like documents use id **`{userId}_{tripId}`** (see `TripLikeDocument.documentId`). Legacy random ids may still exist in old data; APIs still resolve by user/trip where applicable.

## OpenAPI snapshot for the frontend

When you change API contracts and the server is running locally, refresh the checked-in spec the SPA can reference. With a **monorepo** where `frontend` sits next to this directory:

```bash
curl -sS http://localhost:8080/v3/api-docs -o ../frontend/doc/swagger_v2.json
```

If you only have the backend checkout, use the path to your frontend repo’s `doc/swagger_v2.json`, or skip. See also [`frontend/doc/swagger_v2.json`](../frontend/doc/swagger_v2.json) when that path exists. **`/v3/api-docs`** is permitted without authentication.

If the backend is not up or the request fails, do not overwrite the file with a partial error body; skip or fix the environment first.
