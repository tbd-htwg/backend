# Local Minikube state — cluster resources & data flows

Reference for the **local ms2-shaped** stack on Minikube: in-cluster components, localhost access, and the small set of GCP services still used (Identity Platform, GCS).

**Scope:** `kubectl` context `minikube`, namespace `tripplanning`. For setup/teardown, see [README.md](README.md). For GKE / Terraform inventory, see [ms2 STATE.md](../../../infrastructure/ms2/docs/gettingstarted/STATE.md).

---

## 1. Resource inventory

### Minikube cluster

| Category | Resource | Name / pattern |
|----------|----------|----------------|
| **Cluster** | Minikube | Context `minikube`; default driver `docker` |
| **Namespace** | Kubernetes | `tripplanning` |
| **Images** | Local Docker (minikube daemon) | `tripplanning-{trip,social,external-info}-service:local`, `imagePullPolicy: Never` |

### In-cluster (namespace `tripplanning`)

| Component | Implementation | Manifest |
|-----------|----------------|----------|
| **trip-service** | Spring Boot, H2 + ES + Redis | [`k8s/local/trip-service/`](../../k8s/local/trip-service/) |
| **social-service** | Spring Boot, Firestore emulator | [`k8s/local/social-service/`](../../k8s/local/social-service/) |
| **external-info-service** | Spring Boot, Redis | [`k8s/local/external-info-service/`](../../k8s/local/external-info-service/) |
| **Redis** | `redis:7-alpine` | [`infrastructure/ms2/k8s/dependencies/redis/`](../../../infrastructure/ms2/k8s/dependencies/redis/) |
| **Elasticsearch** | Elastic 8.15.x, emptyDir | [`infrastructure/ms2/k8s/dependencies/elasticsearch/`](../../../infrastructure/ms2/k8s/dependencies/elasticsearch/) |
| **Firestore emulator** | `google-cloud-cli:emulators` | [`k8s/local/firestore-emulator/`](../../k8s/local/firestore-emulator/) |

Installed by [`scripts/local-dev.sh`](../../scripts/local-dev.sh) → `install-k8s-dependencies.sh` (Redis/ES) + `kubectl apply -k k8s/local`.

### Host-only

| Component | Role |
|-----------|------|
| **Vite frontend** | `http://localhost:5173` → API via port-forward |
| **nginx Ingress** | Path-based API gateway (`tripplanning-api` in `tripplanning`) |
| **kubectl port-forward** | Default: ingress → `localhost:8080` (all API paths) |
| **gcloud ADC** | Identity token verification, GCS signed URLs (not in-cluster) |

### GCP (used, not provisioned by local scripts)

| Service | Used by | Local behavior |
|---------|---------|----------------|
| **Identity Platform / Firebase** | trip-service (`POST /api/v2/auth/google`) | Real project; optional if using dev-login only |
| **GCS images bucket** | trip-service signed uploads | Real bucket via ADC + SA impersonation in `application-local.yml` |

### Not used locally

Terraform, GKE, Cloud SQL, Cloud Firestore `tbd-firestore`, Artifact Registry, Gateway API, Cloud DNS, cert-manager, frontend GCS bucket deploy.

---

## 2. High-level topology

```mermaid
flowchart TB
  subgraph Host
    Browser[Browser]
    Vite[Vite :5173]
    ADC[gcloud ADC]
  end

  subgraph Minikube["minikube / tripplanning"]
    GW[nginx Ingress]
    Trip[trip-service]
    Social[social-service]
    Ext[external-info-service]
    Redis[(Redis)]
    ES[(Elasticsearch)]
    H2[(H2 emptyDir)]
    FSE[(Firestore emulator)]
  end

  subgraph GCP["GCP milestone2-tbd-cad"]
    IdP[Identity Platform]
    GCS[(GCS images bucket)]
  end

  Browser --> Vite
  Vite -->|port-forward ingress :8080| GW
  GW --> Trip
  GW --> Social
  GW --> Ext
  Trip --> Redis
  Trip --> ES
  Trip --> H2
  Social --> FSE
  Ext --> Redis
  Social -->|internal| Trip
  Trip -->|internal| Social
  Trip -->|verify ID token| IdP
  Trip -->|signed URLs| GCS
  ADC -.-> Trip
```

---

## 3. Local hostnames & routing

| URL (after `./scripts/local-dev.sh port-forward`) | Serves |
|---------------------------------------------------|--------|
| `http://localhost:8080` | **nginx Ingress** — single API entry (same paths as GKE Gateway) |

Optional direct pod/service port-forwards (`:8081`, `:8082`) are for debugging only.

The SPA uses **one** base URL (`VITE_API_BASE_URL=http://localhost:8080` or Vite proxy to the same). **Ingress** routes by path prefix (see [`k8s/local/ingress.yaml`](../../k8s/local/ingress.yaml)):

| Path prefix | Backend |
|-------------|---------|
| `/api/v2/comments`, `/api/v2/trips/.../community`, likes, `countLikes` | social-service |
| `/api/v2/external` | external-info-service |
| `/api/search`, `/api/v2` (catch-all), `/actuator`, auth | trip-service |

---

## 4. Frontend data flow

```mermaid
flowchart LR
  subgraph Browser
    SPA[React SPA]
    FB[Firebase Auth optional]
  end

  subgraph Localhost
    ApiURL["localhost:8080 ingress"]
  end

  subgraph Minikube
    GW[Ingress]
    Trip[trip-service]
    Social[social-service]
    Ext[external-info-service]
  end

  subgraph GCP
    GCS[(GCS images)]
    IdP[Identity Platform]
  end

  SPA -->|JSON API| ApiURL --> GW
  GW --> Trip
  GW --> Social
  GW --> Ext
  FB -->|Google sign-in| SPA
  SPA -->|ID token| Trip
  Trip --> IdP
  SPA -->|PUT signed URL| GCS
```

**Behaviors:**

- Single API entry: `VITE_API_BASE_URL=http://localhost:8080`.
- **dev-login** on trip-service avoids Firebase for local testing.
- Image uploads: signed URL from trip-service; browser PUT to GCS (requires ADC on dev machine).

---

## 5. Backend microservices & data stores

| Service | Container port | K8s Service port | Role |
|---------|----------------|------------------|------|
| trip-service | 8080 | 8080 | Trips, users, locations, auth, search, liked-trips feed |
| social-service | 8081 | 8081 | Likes & comments (Firestore emulator) |
| external-info-service | 8082 | 8082 | Weather, warnings, geocoding, tours (`/api/v2/external`) |

```mermaid
flowchart TB
  subgraph TripPod["trip-service"]
    Trip[trip-service]
  end

  subgraph SocialPod["social-service"]
    Social[social-service]
  end

  subgraph ExtPod["external-info-service"]
    Ext[external-info-service]
  end

  subgraph DataStores
    H2[(H2 file emptyDir)]
    FSE[(Firestore emulator)]
    ES[(Elasticsearch)]
    Redis[(Redis)]
    GCS[(GCS images)]
  end

  subgraph ExternalAPIs
    Nominatim[Nominatim OSM]
    Amt[Auswaertiges Amt]
    Meteo[Open-Meteo]
    Viator[Viator API]
  end

  Trip --> H2
  Trip --> ES
  Trip --> Redis
  Trip --> GCS
  Trip -->|internal HTTP| Social

  Social --> FSE
  Social -->|validate| Trip

  Ext --> Redis
  Ext --> Nominatim
  Ext --> Amt
  Ext --> Meteo
  Ext --> Viator
```

### Per-service data store usage

| Service | SQL | Elasticsearch | Redis | Firestore | GCS | Other HTTP |
|---------|:---:|:-------------:|:-----:|:---------:|:---:|------------|
| **trip-service** | H2 (JPA, no Flyway) | Hibernate Search `tripentity-local` | Cache 10s TTL | — | Signed uploads | social (internal) |
| **social-service** | — | — | — | Emulator `(default)` | — | trip-service |
| **external-info-service** | — | — | Cache 60m TTL | — | — | 4 external APIs; JWT on public routes |

**In-cluster DNS:**

- `elasticsearch.tripplanning.svc.cluster.local:9200`
- `redis.tripplanning.svc.cluster.local:6379`
- `firestore-emulator.tripplanning.svc.cluster.local:8080`
- `trip-service.tripplanning.svc.cluster.local:8080`
- `social-service.tripplanning.svc.cluster.local:8081`
- `external-info-service.tripplanning.svc.cluster.local:8082`

**Inter-service HTTP only** — no message broker. Optional `X-Internal-Secret` on `/internal/**`.

---

## 6. Redis & Elasticsearch

```mermaid
flowchart LR
  subgraph TripFlow["trip-service"]
    Req[API request] --> Cache{Redis cache?}
    Cache -->|hit| Resp[Response]
    Cache -->|miss| H2[(H2)]
    H2 --> Index[Hibernate Search]
    Index --> ES[(Elasticsearch)]
    H2 --> Resp
  end

  subgraph ExtFlow["external-info-service"]
    Req2[details] --> RCache{Redis cache?}
    RCache -->|miss| APIs[External APIs]
    APIs --> RCache
  end
```

| System | Used by | Purpose |
|--------|---------|---------|
| **Elasticsearch** | trip-service (`local,k8s` profile) | Index `tripentity-local`; installed via ms2 dependencies |
| **Redis** | trip-service, external-info-service | Distributed cache; Caffeine fallback if Redis host unset (JVM-only) |

**trip-service Redis cache names** (10s TTL): `tripFeedPage`, `tripFeedByUser`, `tripFeedLikedBy`, `tripDetail`, `tripExists`.

**external-info-service Redis cache names** (60m TTL): `warnings`, `weather`, `tours`.

---

## 7. Secrets

| Kubernetes secret | Source | Keys |
|-------------------|--------|------|
| `trip-service-secrets` | `docs/gettingstarted/.env` | `TRIPPLANNING_AUTH_JWT_SECRET` |
| `social-service-secrets` | same | `TRIPPLANNING_AUTH_JWT_SECRET`, `TRIPPLANNING_INTERNAL_SECRET` |
| `external-info-service-secrets` | same | `TRIPPLANNING_AUTH_JWT_SECRET`, `VIATOR_API_KEY` |

No Secret Manager or External Secrets on the local path. ConfigMaps are applied imperatively by `local-dev.sh` (Firestore host, ES/Redis, CORS, service URLs).

---

## 8. Naming quick reference

```
kubectl context:  minikube
namespace:        tripplanning
image tag:        local
trip-service:     tripplanning-trip-service:local
social-service:   tripplanning-social-service:local
external-info:    tripplanning-external-info-service:local
firestore:        firestore-emulator:8080
GCP project:      milestone2-tbd-cad (auth + GCS)
localhost API:    http://localhost:8080 (ingress port-forward)
frontend dev:     http://localhost:5173
```

---

## 9. Key source files

| Topic | Path |
|-------|------|
| Lifecycle automation | [`scripts/local-dev.sh`](../../scripts/local-dev.sh), [README.md](README.md) |
| Verify smoke tests | [`scripts/verify-local-deployment.sh`](../../scripts/verify-local-deployment.sh) |
| Local K8s manifests | [`k8s/local/`](../../k8s/local/) (includes [`ingress.yaml`](../../k8s/local/ingress.yaml)) |
| Redis / Elasticsearch | [`infrastructure/ms2/k8s/dependencies/`](../../../infrastructure/ms2/k8s/dependencies/) |
| Spring local + k8s profiles | `tripplanning-*/src/main/resources/application-local.yml`, `application-k8s.yml` |
| GKE counterpart | [ms2 gettingstarted](../../../infrastructure/ms2/docs/gettingstarted/) |
| Frontend API client | [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts) |
