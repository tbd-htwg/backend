# Backend microservices (GKE)

**GKE / Terraform platform:** [infrastructure/ms2/docs/README.md](../infrastructure/ms2/docs/README.md) · [Terraform dev env](../infrastructure/ms2/terraform/envs/dev/README.md) — includes **Identity / Firebase** for `POST /api/v2/auth/firebase`.

**Local Minikube (canonical):** [docs/gettingstarted/README.md](docs/gettingstarted/README.md) · [STATE.md](docs/gettingstarted/STATE.md)

Maven multi-module layout:

| Module | Artifact | Port (local) |
|--------|----------|----------------|
| `tripplanning-common` | shared clients, auth properties | — |
| `tripplanning-trip-service` | Cloud SQL / Postgres, search, GCS, auth | 8080 |
| `tripplanning-social-service` | Firestore likes/comments | 8081 |
| `tripplanning-external-info-service` | Google Places, weather, travel warnings, Viator tours, transport distance | 8082 |
| `tripplanning-seed-job` | One-shot perf seed (PostgreSQL + Firestore) | — |

> Ignore repo-root `external-info-service/` — the supported module is `backend/tripplanning-external-info-service`.

## Build

```bash
mvn -pl tripplanning-trip-service -am package
mvn -pl tripplanning-social-service -am package
mvn -pl tripplanning-external-info-service -am package
mvn -pl tripplanning-seed-job -am package
```

## Docker

```bash
docker build --build-arg SERVICE=trip -t tripplanning-trip-service ./backend
docker build --build-arg SERVICE=social -t tripplanning-social-service ./backend
docker build --build-arg SERVICE=external-info -t tripplanning-external-info-service ./backend
docker build --build-arg SERVICE=seed-job -t tripplanning-seed-job ./backend
```

## Local development

### Option A — minikube (recommended for k8s workflow)

See **[docs/gettingstarted/README.md](docs/gettingstarted/README.md)** for the full guide (prerequisites, deploy, verify, frontend, auth, troubleshooting).

```bash
cd backend
cp docs/gettingstarted/.env.example docs/gettingstarted/.env   # JWT_SECRET ≥ 32 chars
./scripts/local-dev.sh setup
./scripts/local-dev.sh port-forward
```

Uses in-cluster **PostgreSQL** (trip), **Redis**, **Elasticsearch**, **Firestore emulator**, and **GCP Identity Platform** + **GCS** (optional Google sign-in and image uploads). Prerequisites:

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project tbd-cloudappdev
kubectl version --client
```

Deploy to GKE:

```bash
./scripts/local-dev.sh use-gke
cd ../infrastructure/ms2/terraform/envs/dev
# follow Terraform / GitOps deploy for your environment
```

### Option B — plain JVM (no Kubernetes)

| Terminal | Command |
|----------|---------|
| Firestore emulator | `gcloud emulators firestore start --host-port=0.0.0.0:9090` |
| External-info (:8082) | `mvn -pl tripplanning-external-info-service spring-boot:run` |
| Social (:8081) | `SPRING_PROFILES_ACTIVE=local mvn -pl tripplanning-social-service spring-boot:run` |
| Trip (:8080) | `SPRING_PROFILES_ACTIVE=local TRIPPLANNING_SOCIAL_SERVICE_URL=http://localhost:8081 TRIPPLANNING_EXTERNAL_INFO_SERVICE_URL=http://localhost:8082 mvn -pl tripplanning-trip-service spring-boot:run` |

## Internal APIs

- Trip: `HEAD /internal/trips/{id}`, `GET /internal/users?ids=`, `POST /internal/cache/trips/liked-by/evict`
- Social: `GET /internal/users/{userId}/liked-trip-ids`
- External-info: `GET /internal/location-pack?placeId=&fresh=` (trip-service enrichment); public routes at `/api/v2/external/**`

Trip-service also exposes: `GET /api/v2/trip-locations/{id}/details` (proxy to external-info for one stop).

Optional header `X-Internal-Secret` when `TRIPPLANNING_INTERNAL_SECRET` is set.

**Host `temp/` directory:** When running trip-service with the `local` profile via Maven (Option B), H2 and Lucene files land under `temp/db/` and `temp/search/` at the backend root. These paths are gitignored except `.gitkeep`; delete runtime files anytime to reset local DB/search state. Minikube uses in-cluster **PostgreSQL** instead.
