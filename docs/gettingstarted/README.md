# Getting started — Trip Planner on Minikube (local ms2)

**Paths:** This guide assumes the **backend** git tree root (`pom.xml` here). Commands use `docs/gettingstarted`, `scripts/local-dev.sh`, and `k8s/local`. In a monorepo, run from `backend/` (e.g. `cd backend` before `./scripts/local-dev.sh`).

For the **GKE / Terraform** stack, see [infrastructure/ms2/docs/README.md](../../../infrastructure/ms2/docs/README.md) and [Terraform dev env](../../../infrastructure/ms2/terraform/envs/dev/README.md). Architecture reference for this environment: [STATE.md](STATE.md).

## TL;DR

**Goal:** Run trip-service, social-service, and external-info-service on **Minikube** with in-cluster PostgreSQL, Valkey, OpenSearch, and Firestore emulator — no Terraform, no GKE, no Cloud SQL.

1. **One-time:** Install tools from [§0](#0-prerequisites). For Google sign-in and GCS image uploads:

   ```bash
   gcloud auth application-default login
   gcloud auth application-default set-quota-project tbd-cloudappdev
   ```

2. **Configure secrets:**

   ```bash
   cd docs/gettingstarted
   cp .env.example .env
   # Edit .env: JWT_SECRET must be ≥ 32 characters; set GOOGLE_MAPS_API_KEY for place search
   ```

3. **Full setup** (from `backend/`):

   ```bash
   ./scripts/local-dev.sh setup
   ./scripts/local-dev.sh port-forward
   ```

4. **Frontend** (separate terminal, from repo `frontend/`):

   ```bash
   npm run dev:minikube
   ```

**After setup:** API at `http://localhost:8080` via ingress port-forward (all services). Use **dev-login** without Google: `POST http://localhost:8080/api/v2/auth/dev-login` with `{"email":"you@local.dev"}`.

> **Canonical automation:** [`scripts/local-dev.sh`](../../scripts/local-dev.sh) implements the steps below. Keep this README and that script in sync when changing flags or commands.

```bash
cd backend   # project root
./scripts/local-dev.sh help
./scripts/local-dev.sh setup
./scripts/local-dev.sh verify
./scripts/local-dev.sh port-forward
```

---

## Local profile (what is included)

Designed for a **single Minikube** cluster with **24 GiB RAM** default (`MINIKUBE_MEMORY=24576`) so OpenSearch, Valkey, Postgres, and three Spring Boot services can start reliably.

| Included | Excluded (use GKE guide instead) |
|----------|----------------------------------|
| Minikube cluster (`tripplanning` namespace) | Terraform / VPC / GKE |
| trip + social + external-info (local images `:local`) | Cloud SQL (uses **in-cluster Postgres** instead) |
| In-cluster **PostgreSQL 16** + **Flyway** (V1–V14) | GKE Gateway, DNS, TLS, cert-manager |
| In-cluster **Valkey** + **OpenSearch** | Real Firestore `tbd-firestore` |
| In-cluster **Firestore emulator** | Artifact Registry push |
| Host `temp/` only for JVM-only dev (gitignored H2/Lucene) | Legacy monolith `src/` tree |
| **GCP Identity Platform** (optional Google sign-in) | Frontend on GCS |
| **GCS images bucket** (signed uploads via ADC) | Flux, kube-prometheus |
| **Google Places API (New)** (place search & enrichment) | |
| **tripplanning-seed-job** (perf dataset via `./scripts/local-dev.sh seed-job`) | |

**Cloud dependencies kept by design:** Identity Platform / Firebase for Google sign-in; GCS for trip/profile image uploads; **Google Places API (New)** for place search and trip/stop/accommodation/transport enrichment. **dev-login** works without Google when `local` profile is active.

---

## What you will run

| Piece | Technology |
|-------|------------|
| Cluster | [Minikube](https://minikube.sigs.k8s.io/) (driver default: `docker`) |
| Trip API | Spring Boot + in-cluster PostgreSQL + OpenSearch + Valkey |
| Social API | Spring Boot + in-cluster Firestore emulator |
| External-info | Spring Boot + Valkey + Google Places / Routes + weather / warnings / Viator |
| Ingress | nginx (minikube addon) — path routing to trip / social / external-info |
| Frontend | Vite dev server → `http://localhost:8080` |

---

## Identity: Google Cloud (optional)

Google sign-in uses the same **Firebase / Identity Platform** project as the GKE stack. See [ms2 overview](../../../infrastructure/ms2/docs/overview.md) for platform context.

**Minimum for local Google sign-in:**

1. OAuth Web client with **`http://localhost:5173`** in authorized JavaScript origins.
2. Frontend `VITE_FIREBASE_*` in `frontend/.env` from Firebase Console → Project settings → Web app (project **tbd-cloudappdev**).
3. `TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID` in `.env` matches that project (default: `tbd-cloudappdev`).
4. `gcloud auth application-default login` on the machine running Minikube (trip-service validates ID tokens via ADC).

**Without Google:** use **dev-login** ([§11](#11-auth-flows)).

---

## 0. Prerequisites

| Tool | Purpose |
|------|---------|
| [Minikube](https://minikube.sigs.k8s.io/docs/start/) | Local Kubernetes |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | Cluster access |
| [Docker](https://docs.docker.com/get-docker/) | Minikube driver + image build |
| Java 21 + Maven 3.9+ | Backend build |
| [gcloud CLI](https://cloud.google.com/sdk/docs/install) | ADC for Identity Platform + GCS |
| [Helm](https://helm.sh/) | Render `k8s/local/chart` manifests (`local-dev.sh deploy`) |
| Node.js 20+ | Frontend dev server (optional) |
| `curl` | Verify script smoke tests |

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project tbd-cloudappdev
```

**Optional — host Firestore emulator** (debug only; default is in-cluster):

```bash
gcloud components install cloud-firestore-emulator
USE_HOST_FIRESTORE_EMULATOR=true ./scripts/local-dev.sh setup
```

**Optional — `.env`:** Copy [`.env.example`](.env.example) to `.env` in this directory. [`local-dev.sh`](../../scripts/local-dev.sh) sources it automatically (then `backend/.env.local`, then ms2 gettingstarted `.env` if present).

---

## 1. Configure secrets

```bash
cd docs/gettingstarted
cp .env.example .env
```

| Variable | Required | Notes |
|----------|----------|-------|
| `JWT_SECRET` | Yes | ≥ 32 characters; signs app JWT after login |
| `INTERNAL_SECRET` | No | Default `dev-internal-service-secret`; protects `/internal/**` on social-service and external-info-service (trip-service sends `X-Internal-Secret`) |
| `GOOGLE_PROJECT` | No | Default `tbd-cloudappdev` (local dev GCP project) |
| `TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID` | No | Defaults to `GOOGLE_PROJECT` |
| `GOOGLE_MAPS_API_KEY` | Yes (place search) | Places API (New) for `/api/v2/external/details/search` and enrichment; enable `places.googleapis.com` |
| `VIATOR_API_KEY` | No | Real Viator tour data in external-info-service |

---

## 2. Teardown

No Terraform. Stop or delete the Minikube cluster:

```bash
./scripts/local-dev.sh stop      # stop minikube
./scripts/local-dev.sh delete    # delete cluster + clean .local-dev/pids
```

To redeploy without deleting the cluster: `./scripts/local-dev.sh deploy`.

---

## 3. Cluster bootstrap

Starts Minikube if needed and sets kubectl context to `minikube`:

```bash
./scripts/local-dev.sh use-local
```

| Variable | Default | Purpose |
|----------|---------|---------|
| `MINIKUBE_CPUS` | `4` | CPU for `minikube start` |
| `MINIKUBE_MEMORY` | `24576` | Memory in MiB (24 GiB) |
| `MINIKUBE_DRIVER` | `docker` | VM driver |

---

## 4. Kubernetes secrets

Created by `local-dev.sh` from `.env` (not Secret Manager):

| Secret | Keys |
|--------|------|
| `trip-service-secrets` | `TRIPPLANNING_AUTH_JWT_SECRET`, `TRIPPLANNING_INTERNAL_SECRET` |
| `social-service-secrets` | `TRIPPLANNING_AUTH_JWT_SECRET`, `TRIPPLANNING_INTERNAL_SECRET` |
| `external-info-service-secrets` | `TRIPPLANNING_AUTH_JWT_SECRET`, `TRIPPLANNING_INTERNAL_SECRET`, `GOOGLE_MAPS_API_KEY`, `VIATOR_API_KEY` (optional) |

Re-apply after changing `.env`: `./scripts/local-dev.sh deploy`.

---

## 5. Deploy

Builds JARs, images in the Minikube Docker daemon, renders `k8s/local/chart` with Helm (Valkey + OpenSearch subcharts), applies Postgres + apps + Firestore emulator:

```bash
./scripts/local-dev.sh deploy
```

Steps inside `deploy`:

1. `mvn package` (trip, social, external-info, seed-job modules)
2. `docker build` with `imagePullPolicy: Never` tags `tripplanning-*-service:local`
3. `helm template` → `kubectl apply` from `k8s/local/chart` (see `k8s/local/README.md`)
4. ConfigMaps + rollout restart

**First trip-service start** can take 1–3 minutes (OpenSearch mass indexing via `SearchIndexCoordinationService`).

With ingress port-forward running, optional **debug UIs** from the Helm chart: `/debug/valkey` (Valkey Admin), `/debug/opensearch` (OpenSearch Dashboards), `/debug/external` (external-info internal debug).

---

## 6. Verify

```bash
./scripts/local-dev.sh verify
```

Checks pod readiness, actuator health via port-forward, and optional **dev-login** smoke test. Set `VERIFY_STRICT=true` to exit non-zero on failure.

---

## 7. Access APIs

```bash
./scripts/local-dev.sh port-forward
```

| Entry | Local URL | Notes |
|-------|-----------|--------|
| API (ingress) | `http://localhost:8080` | `./scripts/local-dev.sh port-forward` → nginx ingress |
| trip-service (debug) | `:8080` in-cluster | `trip-service.tripplanning.svc.cluster.local:8080` |
| social-service (debug) | `:8081` in-cluster | `social-service.tripplanning.svc.cluster.local:8081` |
| external-info-service (debug) | `:8082` in-cluster | `external-info-service.tripplanning.svc.cluster.local:8082` |
| Valkey Admin (debug) | `http://localhost:8080/debug/valkey/` | Trailing slash required (Vite relative assets); redirects from `/debug/valkey` |
| OpenSearch Dashboards (debug) | `http://localhost:8080/debug/opensearch` | Dev Tools, index `tripentity-local` |
| search-index debug | `http://localhost:8080/internal/debug/search-index` | Trip-service ES indexing status |

**API smoke tests** (requires `GOOGLE_MAPS_API_KEY` and Places API New enabled on the GCP project):

```bash
# Place search (returns placeId for trip/stop writes)
curl -s 'http://localhost:8080/api/v2/external/details/search?q=Paris' | jq .

# Stop weather + travel warning (use placeId from search)
curl -s 'http://localhost:8080/api/v2/external/stop-details?placeId=ChIJD7fiBh9u5kcRYJSMaMOCCwQ' | jq .
```

Transport routes are computed in the **frontend** via Google Routes API (`VITE_GOOGLE_MAPS_API_KEY`); there is no backend `/transport/route` proxy.

**Routing:** The SPA uses **one** origin (`http://localhost:8080` or Vite proxy). **Ingress** routes social paths (`/api/v2/comments`, trip community, likes, `countLikes`) and `/api/v2/external/*` to the correct service (see [`values-local.yaml`](../../k8s/local/chart/values-local.yaml) `ingressRoutes`; GKE counterpart: [`api-httproute.yaml`](../../../infrastructure/ms2/charts/tripplanning/templates/routes/api-httproute.yaml)).

**Logs:**

```bash
./scripts/local-dev.sh logs trip-service
./scripts/local-dev.sh status
```

---

## 8. Places & external info (API contract)

Trips, stops, accommodations, and transports use **Google Places** IDs — not free-text locations or the removed `/api/v2/locations` catalog.

**Typical flow:**

1. Search: `GET /api/v2/external/details/search?q=…` → pick a `placeId` from results.
2. Write: send that `placeId` on trip/stop/accommodation/transport create or update.
3. Enrichment: trip-service calls external-info `GET /internal/location-pack?placeId=…&fresh=true` and upserts the **`google_places`** cache table.

| Resource | Write endpoint | Required fields |
|----------|----------------|-----------------|
| Trip | `POST /api/v2/trips` (SDR) | `destinationGooglePlaceId` — `destination` label is server-derived |
| Trip stop | `POST /api/v2/trip-locations` | `googlePlaceId` (replaces legacy `locationId`) |
| Accommodation | `POST` / `PUT /api/v2/accommodations` | `{ googlePlaceId, checkInDate, checkOutDate, cost, currency }` — name/address enriched server-side; SDR `POST` disabled |
| Transport | `POST` / `PUT /api/v2/transports` | `{ startGooglePlaceId, endGooglePlaceId }` — addresses enriched server-side; no `type` or cost; SDR `POST` disabled |

**Trip feed reads** (`GET /api/v2/trips/feed`, `/feed/by-user`, `/feed/liked-by`, `/{id}/detail`):

- Feed cards: **`transportRoutes`** (`"start addr → end addr"`) replaces **`transportTypes`**.
- Detail stops include full place fields (`googlePlaceId`, `placeName`, `cityName`, coordinates, address).
- Accommodations include dates, cost, currency, and place metadata.
- Unknown trip id on `/{id}/detail` returns **404**.

**external-info public endpoints** (via ingress `/api/v2/external`):

| Endpoint | Purpose |
|----------|---------|
| `GET /details/search?q=` | Google Places text search |
| `GET /stop-details`, `/stop-details/batch` | Weather + travel warnings for a place |
| `GET /accommodation-details`, `/accommodation-details/batch` | Viator tours for accommodation cost context |

Transport polylines: **frontend** calls Google Routes API directly (see [frontend README](../../../frontend/README.md)).
| `GET /details`, `/details/batch` | Deprecated; use `placeId` param instead of free-text location |

**Removed:** Nominatim geocoding, `/api/v1/details/**`, `/api/v2/locations`.

---

## 9. Performance dataset (seed-job)

For the **5k users / 15k trips** perf dataset (PostgreSQL + Firestore likes/comments):

```bash
./scripts/local-dev.sh sync-sample-images   # one-time: upload sample images to GCS
./scripts/local-dev.sh seed-job               # wipe + seed; writes perf_seed_manifest.json
```

Output manifest: `performance/seeding_example/perf_seed_manifest.json` (used by Locust). See [`tripplanning-seed-job/README.md`](../../tripplanning-seed-job/README.md).

For a **small smoke dataset** via HTTP only, use [`performance/seeding_example/seed_example_data.py`](../../../performance/seeding_example/seed_example_data.py) instead.

---

## 10. Frontend

From the `frontend/` directory (with `./scripts/local-dev.sh port-forward` running):

```bash
cp .env.example .env   # set VITE_FIREBASE_* for Google sign-in (optional for dev-login)
npm run dev:minikube
```

Open `http://localhost:5173`. Vite proxies `/api/v2` to `http://localhost:8080` (ingress). CORS is configured on trip-, social-, and external-info-service for `http://localhost:5173` and `http://127.0.0.1:5173`.

For the **GKE** API instead: `npm run dev:k8s` (proxies to `https://k8s.tbd-htwg.de`).

---

## 11. Auth flows

### Dev login (no Google)

With `SPRING_PROFILES_ACTIVE=local,k8s,postgres` on trip-service:

```bash
curl -sS -X POST http://localhost:8080/api/v2/auth/dev-login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@local.test","name":"Dev User"}'
```

Returns `accessToken` for `Authorization: Bearer …` on `/api/v2/*`.

### Google sign-in

1. Configure Identity Platform / Firebase (see [ms2 overview](../../../infrastructure/ms2/docs/overview.md)).
2. Frontend obtains Firebase ID token → `POST /api/v2/auth/firebase` on trip-service (deprecated alias: `/auth/google`).
3. Requires **Application Default Credentials** on the host (Minikube pods use the node's credential chain for GCP client libraries where configured).

---

## 12. GCS images

Trip-service uses the **tbd-cloudappdev** dev bucket and signer SA, configured via ConfigMap / [`application-local.yml`](../../tripplanning-trip-service/src/main/resources/application-local.yml):

| Setting | Default (local Minikube) |
|---------|--------------------------|
| Bucket | `tbd-test` |
| Signer SA | `tripplanning-image-url-sig@tbd-cloudappdev.iam.gserviceaccount.com` |

Override in `.env` with `GCP_STORAGE_BUCKET_NAME` and `GCP_IMPERSONATE_SERVICE_ACCOUNT` if your console setup differs.

For **GKE / ms2** (`tbd-cloudappdev`), use the ms2 getting-started guide and Terraform-managed bucket instead.

### One-time setup (image uploads)

1. **GCP console** (or `./scripts/local-dev.sh setup-gcs-iam`): bucket `tbd-test` and signer SA `tripplanning-image-url-sig` in project **tbd-cloudappdev**, plus IAM for your user to impersonate the SA.

2. **Application Default Credentials** on your machine:

   ```bash
   gcloud auth application-default login
   gcloud auth application-default set-quota-project tbd-cloudappdev
   ```

   `local-dev.sh deploy` syncs `~/.config/gcloud/application_default_credentials.json` into Kubernetes secret **`gcp-adc`** and mounts it into the trip-service pod.

3. **Impersonation** — your user must be allowed to mint tokens for the signer SA (Terraform grants this to the GKE workload SA only). If signed URLs fail with impersonation errors, run once:

   ```bash
   gcloud iam service-accounts add-iam-policy-binding \
     tripplanning-image-url-sig@tbd-cloudappdev.iam.gserviceaccount.com \
     --member="user:$(gcloud config get-value account)" \
     --role="roles/iam.serviceAccountTokenCreator" \
     --project="tbd-cloudappdev"
   ```

   Or run the automated IAM setup (creates SA if missing, bucket + impersonation bindings):

   ```bash
   ./scripts/local-dev.sh setup-gcs-iam
   ```

4. **Bucket CORS** (browser PUT from `localhost:5173`):

   ```bash
   ./scripts/local-dev.sh setup-gcs
   ```

**Upload flow:** trip-service returns a signed URL; the browser **PUTs** directly to GCS (not through port-forward).

Trips **without** images work without steps 3–4.

### Firestore composite index (GKE / real Firestore only)

On **ms2 GKE deploy**, ensure the Firestore composite index on **`comments`** exists in database **`tbd-firestore`**:

- `tripId` (ascending)
- `createdAt` (descending)

That index is **required** for paginated comment lists on **production Firestore**. It is **not** created by `local-dev.sh`.

**Minikube** uses the **in-cluster Firestore emulator**, which typically does **not** enforce composite indexes — you usually do **not** need to create the index locally. Comment **create** (`POST /api/v2/comments`) never needs the index.

To create the index manually on GCP (e.g. after using real Firestore), use the Firebase console or `gcloud firestore indexes composite create` for the `comments` collection group.

---

## 13. Switch to GKE

Before running ms2 cloud deploy, restore GKE kubectl context (stops Minikube by default):

```bash
./scripts/local-dev.sh use-gke
cd ../infrastructure/ms2/terraform/envs/dev
# follow Terraform / GitOps deploy for your environment
```

---

## `local-dev.sh` command reference

| Command | README § | Description |
|---------|----------|-------------|
| `setup` | §3–§6 | `use-local` + secrets + `deploy` + `verify` |
| `start` | §3–§6 | `use-local` + `deploy` (no verify) |
| `use-local` | §3 | Start minikube, set context |
| `use-gke` | §13 | Restore GKE credentials |
| `deploy` | §5 | Build + kubectl apply |
| `verify` | §6 | Health smoke tests |
| `setup-gcs-iam` | §12 | Signer SA + bucket IAM + user impersonation (one-time) |
| `setup-gcs` | §12 | Apply GCS bucket CORS |
| `sync-sample-images` | §9 | Upload `_sample_images/` to GCS (one-time, before seed-job) |
| `seed-job` | §9 | Wipe + seed PostgreSQL + Firestore perf dataset |
| `port-forward` | §7 | ingress → localhost :8080 |
| `status` | — | Mode, context, pods |
| `logs [svc]` | §7 | Tail deployment logs |
| `stop` | §2 | Stop minikube |
| `delete` | §2 | Delete minikube cluster |
| `help` | — | Usage |

---

## Checklist

| Step | Command |
|------|---------|
| ☐ Tools installed | [§0](#0-prerequisites) |
| ☐ ADC login | `gcloud auth application-default login` |
| ☐ `.env` configured | `cp docs/gettingstarted/.env.example docs/gettingstarted/.env` (incl. `GOOGLE_MAPS_API_KEY`) |
| ☐ Cluster + deploy | `./scripts/local-dev.sh setup` |
| ☐ Verify | `./scripts/local-dev.sh verify` |
| ☐ Port-forward | `./scripts/local-dev.sh port-forward` |
| ☐ Frontend | `npm run dev:minikube` (from `frontend/`) |
| ☐ Dev login or Google | [§11](#11-auth-flows) |
| ☐ GCS (optional) | ADC login + `setup-gcs-iam` + `setup-gcs` [§12](#12-gcs-images) |
| ☐ Perf dataset (optional) | `sync-sample-images` + `seed-job` [§9](#9-performance-dataset-seed-job) |

---

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| **503 on place search / 400 on trip writes** | Missing or invalid `GOOGLE_MAPS_API_KEY`; enable `places.googleapis.com` on the GCP project |
| **502 on accom/transport create** | external-info unreachable or `INTERNAL_SECRET` mismatch between trip-service and external-info-service |
| **trip-service Not Ready for several minutes** | Expected during ES mass indexing (`SearchIndexCoordinationService`); check `GET /internal/debug/search-index` via ingress |
| **trip-service CrashLoop / not Ready** | OpenSearch still starting — `kubectl logs -n tripplanning deployment/trip-service --tail=80`. Raise `MINIKUBE_MEMORY`. |
| **HSEARCH400075 / Lucene analysis configurer** | Rebuild image after fix: `local,k8s` must not load Lucene search config (see `application-local-lucene.yml`). Run `./scripts/local-dev.sh deploy`. |
| **500 on `/trips/*/community`** | trip-service called `localhost:8081` from inside the pod — rebuild/deploy after `application-local-k8s-services.yml` fix. Check `kubectl logs deployment/trip-service`. |
| **404 on `POST /api/v2/comments`** | Ingress must route `/api/v2/comments` to social-service — check `kubectl get ingress -n tripplanning` and rebuild/deploy. |
| **Comment list 500 / index errors** | Firestore emulator usually needs no manual DB; composite indexes are required on **real** Firestore (GKE). Emulator often works without them. |
| **500 on trip child resources** (`/accommodations`, etc.) | OpenSearch pod OOM/crash — `kubectl get pods -l app.kubernetes.io/name=opensearch`. Re-apply: `./scripts/local-dev.sh deploy` then restart trip-service. |
| **social-service Firestore errors** | `kubectl logs -n tripplanning deployment/social-service`; ensure `firestore-emulator` pod is Running. |
| **ImagePullBackOff** | Images must be built **inside** minikube Docker (`eval "$(minikube docker-env)"` is done by `deploy`). Tag must be `:local` with `imagePullPolicy: Never`. |
| **dev-login 404** | Trip must use `SPRING_PROFILES_ACTIVE=local,k8s,postgres` (ConfigMap from `local-dev.sh`). |
| **Google sign-in fails** | ADC, OAuth origins, `VITE_FIREBASE_*`, test users on OAuth consent screen. |
| **GCS upload 401 / Anonymous caller** | Run `gcloud auth application-default login`, then `./scripts/local-dev.sh deploy` (syncs `gcp-adc` secret). |
| **signBlob / impersonation denied** | Grant yourself `roles/iam.serviceAccountTokenCreator` on `tripplanning-image-url-sig@…` — see [§12](#12-gcs-images). |
| **Browser PUT blocked by CORS** | `./scripts/local-dev.sh setup-gcs` |
| **OOM / ES evicted** | Increase `MINIKUBE_MEMORY` (e.g. `32768`). |
| **Host vs in-cluster Firestore** | Default in-cluster `firestore-emulator:8080`. Host fallback: `USE_HOST_FIRESTORE_EMULATOR=true`. |
| **Stale H2 / Lucene on host** | Safe to delete `temp/db/*` and `temp/search/*` (keeps `.gitkeep`); only used when running trip-service with `local` profile via Maven on the host, not Minikube (Minikube uses Postgres). |

**Recovery after partial deploy:**

```bash
./scripts/local-dev.sh deploy
./scripts/local-dev.sh verify
```

**Compare with GKE:** [ms2 overview](../../../infrastructure/ms2/docs/overview.md) · [ms2 docs README](../../../infrastructure/ms2/docs/README.md)
