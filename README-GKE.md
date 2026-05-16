# Backend microservices (GKE)

**Full platform steps (Terraform, kubectl, deploy):** [infrastructure/ms2/docs/gettingstarted/README.md](../infrastructure/ms2/docs/gettingstarted/README.md) — includes **Identity / Firebase** for `POST /api/v2/auth/google`.

Maven multi-module layout:

| Module | Artifact | Port (local) |
|--------|----------|----------------|
| `tripplanning-common` | shared clients, auth properties | — |
| `tripplanning-trip-service` | Cloud SQL, search, GCS, auth | 8080 |
| `tripplanning-social-service` | Firestore likes/comments | 8081 |
| `tripplanning-external-info-service` | Weather, travel warnings, geocoding, tours | 8082 |

## Build

```bash
mvn -pl tripplanning-trip-service -am package
mvn -pl tripplanning-social-service -am package
mvn -pl tripplanning-external-info-service -am package
```

## Docker

```bash
docker build --build-arg SERVICE=trip -t tripplanning-trip-service ./backend
docker build --build-arg SERVICE=social -t tripplanning-social-service ./backend
docker build --build-arg SERVICE=external-info -t tripplanning-external-info-service ./backend
```

## Local development

### Option A — minikube (recommended for k8s workflow)

```bash
cd backend
cp .env.local.example .env.local   # edit JWT_SECRET (≥32 chars)
./scripts/local-dev.sh setup
./scripts/local-dev.sh port-forward
```

Uses H2 (trip), in-cluster **Redis + Elasticsearch** (plain K8s manifests via `install-k8s-dependencies.sh`), **gcloud Firestore emulator** (social data), and **GCP Identity Platform** (real Firebase auth project). Prerequisites:

```bash
gcloud components install cloud-firestore-emulator
gcloud auth application-default login
gcloud auth application-default set-quota-project milestone2-tbd-cad
kubectl version --client   # required for Redis/ES install script
```

Trip and external-info use **Redis** for cache when `SPRING_DATA_REDIS_HOST` is set (k8s ConfigMaps). JVM-only runs without Redis use **Caffeine** in-process.

Deploy to GKE (`dev-lifecycle.sh` sets the GKE kubectl context automatically):

```bash
cd ../infrastructure/ms2/docs/gettingstarted
./dev-lifecycle.sh deploy
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
- External-info (cluster-internal): `GET /api/v1/details`, `GET /api/v1/details/search`

Trip-service exposes a gateway: `GET /api/v2/external/details` and `GET /api/v2/trip-locations/{id}/details`.

Optional header `X-Internal-Secret` when `TRIPPLANNING_INTERNAL_SECRET` is set.
