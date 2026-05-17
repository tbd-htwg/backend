# Agent notes — trip planning backend

Concise context for AI assistants and contributors working in this directory. For full runbooks, see [README.md](README.md) and [docs/gettingstarted/README.md](docs/gettingstarted/README.md).

**Working directory:** Commands below assume the **root of the backend project** (where `pom.xml` lives). In a **monorepo** that is often `…/backend/` under a top-level folder; if you cloned **only** the backend repository, your shell is already that root—**do not** add an extra `backend/` prefix. The same idea applies to paths such as `../frontend/…`: they only work when the **frontend** tree exists next to **this** tree (typical monorepo layout). If your checkout does not include the frontend, skip cross-repo file operations or point to your actual path.

## What this is

Maven **multi-module** Spring Boot 3 backend:

| Module | Port | Role |
|--------|------|------|
| `tripplanning-trip-service` | 8080 | Trips, users, locations, auth, GCS images, search |
| `tripplanning-social-service` | 8081 | Firestore comments / likes |
| `tripplanning-external-info-service` | 8082 | Weather, warnings, geocoding, Viator tours |
| `tripplanning-common` | — | Shared clients and auth config |

**Spring Data REST** on trip-service exposes domain resources under **`/api/v2`**. **OpenAPI 3** JSON: **`/v3/api-docs`** (public). Swagger UI: **`/swagger-ui/index.html`**.

**Recommended local stack:** Minikube via [`scripts/local-dev.sh`](scripts/local-dev.sh) — see [docs/gettingstarted/README.md](docs/gettingstarted/README.md).

## Run locally (`local` profile, JVM-only)

See [README-GKE.md](README-GKE.md) Option B for all three services. Trip-service only:

```bash
gcloud emulators firestore start --host-port=localhost:9090
SPRING_PROFILES_ACTIVE=local mvn -pl tripplanning-trip-service spring-boot:run
```

- **H2** file DB: `./temp/db/tripplanning-dev` (gitignored; recreated on run); **Flyway off**; JPA **`create-drop`** each run.
- **Hibernate Search** uses the **Lucene** backend; indexes under **`./temp/search`** (gitignored).
- **Minikube** uses in-pod H2 (`emptyDir` at `/app/temp`) and in-cluster Elasticsearch — not the host `temp/` directory.
- **Firestore** for social: emulator on **`localhost:9090`** (JVM path) or in-cluster `firestore-emulator` (k8s path).
- Auth: override with **`TRIPPLANNING_AUTH_JWT_SECRET`** (≥32 bytes); **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** for Google token verification.

Default trip-service: **`http://localhost:8080`**.

## Architecture (where things live)

| Area | Module / package |
|------|------------------|
| Core domain (JPA + REST) | `tripplanning-trip-service` — `user`, `trip`, `tripLocation`, `location`, `accommodation`, `transport` |
| Auth (Google ID token → app JWT) | `tripplanning-trip-service` — `com.tripplanning.auth` |
| Social (Firestore) | `tripplanning-social-service` — `com.tripplanning.social` |
| Full-text search | `tripplanning-trip-service` — `com.tripplanning.search` — **`/api/search/**`** |
| Images (GCS) | `tripplanning-trip-service` — `com.tripplanning.images` |
| External APIs | `tripplanning-external-info-service` — `com.tripplanning.externalinfo` |
| Security, OpenAPI | `tripplanning-trip-service` — `com.tripplanning.api.config` |
| JSON projections | `tripplanning-trip-service` — `com.tripplanning.api.projections` |

Production-like runs use **PostgreSQL**, **Flyway** in `tripplanning-trip-service/src/main/resources/db/migration`, **Elasticsearch** for Hibernate Search. **Not** the same as `local`.

## Conventions

- Keep new code aligned with existing packages and Spring patterns in the correct module.
- **Do not** turn Flyway back on for `local` without an intentional workflow change (Postgres-flavored migrations do not match ad-hoc H2).
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
