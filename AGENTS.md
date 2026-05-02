# Agent notes — trip planning backend

Concise context for AI assistants and contributors working in this directory. For full runbooks, see [README.md](README.md).

**Working directory:** Commands below assume the **root of the backend project** (where `pom.xml` lives). In a **monorepo** that is often `…/backend/` under a top-level folder; if you cloned **only** the backend repository, your shell is already that root—**do not** add an extra `backend/` prefix. The same idea applies to paths such as `../frontend/…`: they only work when the **frontend** tree exists next to **this** tree (typical monorepo layout). If your checkout does not include the frontend, skip cross-repo file operations or point to your actual path.

## What this is

Spring Boot 3 API for a trip-planning app: **Spring Data REST** exposes domain resources under **`/api/v2`**. **OpenAPI 3** JSON: **`/v3/api-docs`** (public). Swagger UI: **`/swagger-ui/index.html`**.

## Run locally (`local` profile)

From the backend project root:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

- **H2** file DB: `./temp/db/tripplanning-dev`; **Flyway is off**; JPA **`create-drop`** each run.
- **Hibernate Search** uses the **Lucene** backend; indexes under **`./temp/search`**.
- **Firestore** for comments/likes: start the emulator on **`localhost:9090`** (must match `application-local.yml`). Either:
  - **Firebase CLI** (from this directory, uses [`firebase.json`](firebase.json)): `firebase emulators:start --only firestore`
  - **Google Cloud SDK:** `gcloud emulators firestore start --host-port=localhost:9090`
- Auth: `application-local.yml` supplies a dev JWT secret; override with **`TRIPPLANNING_AUTH_JWT_SECRET`** (≥32 bytes) in shared environments. **`TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID`** for Google token verification.

Default server: **`http://localhost:8080`**.

## Architecture (where things live)

| Area | Package / notes |
|------|------------------|
| Core domain (JPA + REST) | `user`, `trip`, `tripLocation`, `location`, `accommodation`, `transport` |
| Auth (Google ID token → app JWT) | `com.tripplanning.auth` |
| Social (Firestore documents) | `com.tripplanning.social` — comments and likes |
| Full-text search | `com.tripplanning.search` — **`/api/search/**`** (GET public per security config) |
| Images (GCS) | `com.tripplanning.images` |
| Security, OpenAPI, GCP helpers | `com.tripplanning.api.config`, `com.tripplanning.config` |
| JSON shapes for list/detail | `com.tripplanning.api.projections` |

Production-like runs use **PostgreSQL**, **Flyway** migrations in `src/main/resources/db/migration`, **Elasticsearch** for Hibernate Search (see `application.yml`). **Not** the same as `local`.

## Conventions

- Keep new code aligned with existing packages and Spring patterns.
- **Do not** turn Flyway back on for `local` without an intentional workflow change (Postgres-flavored migrations do not match ad-hoc H2).
- HTTP security: [`SecurityConfig.java`](src/main/java/com/tripplanning/api/config/SecurityConfig.java) — adjust matchers when adding routes.

## Firestore like document IDs

New like documents use id **`{userId}_{tripId}`** (see `TripLikeDocument.documentId`). Legacy random ids may still exist in old data; APIs still resolve by user/trip where applicable.

## OpenAPI snapshot for the frontend

When you change API contracts and the server is running locally, refresh the checked-in spec the SPA can reference. With a **monorepo** where `frontend` sits next to this directory:

```bash
curl -sS http://localhost:8080/v3/api-docs -o ../frontend/doc/swagger_v2.json
```

If you only have the backend checkout, use the path to your frontend repo’s `doc/swagger_v2.json`, or skip. See also [`frontend/doc/swagger_v2.json`](../frontend/doc/swagger_v2.json) when that path exists. **`/v3/api-docs`** is permitted without authentication.

If the backend is not up or the request fails, do not overwrite the file with a partial error body; skip or fix the environment first.
