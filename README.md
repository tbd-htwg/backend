# Trip Planning Backend

Spring Boot backend for a simple trip planning application.

## Prerequisites

- Java 21
- Maven 3.9+

## Run locally with Google Sign-In (identity provider)

Use the **`local`** profile so you get H2, Lucene search, and a default dev JWT secret from `application-local.yml`. If needed, override the Firebase project id used for ID token verification.

**One shell command** (from the `backend` directory):

```bash
SPRING_PROFILES_ACTIVE=local \
TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID='project-9118634e-c9f1-4f29-804' \
mvn spring-boot:run
```

To override the dev JWT signing key as well (optional; otherwise `application-local.yml` provides a default):

```bash
SPRING_PROFILES_ACTIVE=local \
TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID='project-9118634e-c9f1-4f29-804' \
TRIPPLANNING_AUTH_JWT_SECRET='your-own-secret-at-least-32-bytes-long' \
mvn spring-boot:run
```

Then start the frontend and open the app at `http://localhost:5173`.

## Run Locally

1. Set auth environment variables (required unless you use the `local` profile defaults below):

   - `TRIPPLANNING_AUTH_JWT_SECRET` — at least **32 UTF-8 bytes** (used to sign application JWTs after Google sign-in).
   - `TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID` — Firebase project id used to validate incoming ID tokens for `POST /api/v2/auth/google`.

   On **Cloud Run** (GitHub Actions deploy workflow), `TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID` is set from **`GCP_PROJECT_ID`** — no separate Actions variable is needed.

   For quick local runs with H2 + Lucene, use the `local` profile (see `application-local.yml`): it supplies a **dev-only default JWT secret** and enables `POST /api/v2/auth/dev-login` so you can obtain a token without Google.

   ```bash
   SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
   ```

2. Start the application (default / Postgres profile requires DB and secrets from your environment):

```bash
mvn spring-boot:run
```

3. The API is available at:
   - `http://localhost:8080/api/v2`

4. H2 database setup (legacy / non-Flyway default docs):
   - Database is stored in `./db/tripplanning` (file-based H2).
   - H2 console is enabled at `http://localhost:8080/h2-console`.
   - JDBC URL: `jdbc:h2:file:./db/tripplanning;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`
   - User: `sa`
   - Password: (empty)

## Seed Example Data

Seed realistic demo data (users, trips, locations, accommodations, transports, likes, comments):

```bash
python3 scripts/seed_example_data.py
```

With custom API target:

```bash
ROOT_URL=http://localhost:8080 BASE_PATH=/api/v2 python3 scripts/seed_example_data.py
```

If your deployment exposes the API under a prefix, set `BASE_PATH` accordingly (for example `/api/v2`).

Mutating API calls require a JWT (`Authorization: Bearer …`). Point the script at a token from Google login or `POST /api/v2/auth/dev-login` when using the `local` profile.

## API Reference (OpenAPI / Swagger)

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

These endpoints provide a frontend-friendly reference for request/response schemas and available routes.

## Authentication (Google Identity Platform + application JWT)

Hand-written REST controllers under `/api/v2/auth`:

- `POST /api/v2/auth/google` — body `{ "credential": "<Firebase ID token>" }`; verifies the token, creates or links the user (`google_sub`, email), returns `{ "tokenType", "accessToken", "user" }`.
- `GET /api/v2/auth/me` — returns the current user; requires `Authorization: Bearer <accessToken>`.
- `POST /api/v2/auth/dev-login` — **only when `spring.profiles.active` includes `local`**; body `{ "email", "name?" }`; returns the same JSON as Google login for local testing without Google.

Other `/api/v2/**` routes: **GET** is mostly public (except user collection and user search, which require a valid JWT). **POST/PUT/PATCH/DELETE** require `Authorization: Bearer <accessToken>`.

**Google Identity Platform/Firebase:** ensure your frontend origin is authorized in your Firebase Authentication settings.

## Current API Endpoints

This project uses Spring Data REST repositories for domain resources, plus the auth controllers above.
Repository endpoints are inferred and are available under the base path `/api/v2`.

Main collection resources currently exposed:

- `GET/POST /api/v2/users`
- `GET/PUT/PATCH/DELETE /api/v2/users/{id}`
- `GET/POST /api/v2/trips`
- `GET/PUT/PATCH/DELETE /api/v2/trips/{id}`

Depending on repository methods, Spring Data REST may additionally expose search endpoints under:

- `/api/v2/users/search`
- `/api/v2/trips/search`

## Project Structure (Overview)

- `com.tripplanning`
  - Spring Boot application entrypoint (`Application`).
- `com.tripplanning.user`
  - `UserEntity` and `UserRepository` (Spring Data REST resource `/api/v2/users`).
- `com.tripplanning.trip`
  - `TripEntity` and `TripRepository` (Spring Data REST resource `/api/v2/trips`).
- `com.tripplanning.accommodation`
  - `AccomEntity` and `AccomRepository`.
- `com.tripplanning.transport`
  - `TransportEntity` and `TransportRepository`.
- `com.tripplanning.comment`
  - `CommentEntity` and `CommentRepository`.
- `com.tripplanning.location`
  - `LocationEntity` and `LocationRepository`.
- `com.tripplanning.tripLocation`
  - `TripLocationEntity` and `TripLocationRepository`.
- `com.tripplanning.api.config`
  - Security and OpenAPI configuration.

## Firestore `likes` document IDs

New like rows use a deterministic document id `{userId}_{tripId}` so a single read/delete can resolve membership without a field query.

**Existing deployments:** documents created before this change keep random Firestore ids; APIs that query by `userId` and `tripId` still find them. Optional cleanup: delete duplicate legacy docs if the same user/trip was ever written twice, or migrate old docs to ids `{userId}_{tripId}` and remove the random-id copies.

## Run Tests

```bash
mvn test
```

## Docker

Build image:

```bash
docker build -t trip-backend:local .
```

Run container:

```bash
docker run --rm -p 8080:8080 trip-backend:local
```

API base URL inside local Docker run:
- `http://localhost:8080/api/v2`
