# Getting started — Trip Planner on Minikube (local ms2)

**Paths:** This guide assumes the **backend** git tree root (`pom.xml` here). Commands use `docs/gettingstarted`, `scripts/local-dev.sh`, and `k8s/local`. In a monorepo, run from `backend/` (e.g. `cd backend` before `./scripts/local-dev.sh`).

For the **GKE / Terraform** stack, see [infrastructure/ms2/docs/gettingstarted/README.md](../../../infrastructure/ms2/docs/gettingstarted/README.md). Architecture reference for this environment: [STATE.md](STATE.md).

## TL;DR

**Goal:** Run trip-service, social-service, and external-info-service on **Minikube** with in-cluster Redis, Elasticsearch, H2, and Firestore emulator — no Terraform, no GKE, no Cloud SQL.

1. **One-time:** Install tools from [§0](#0-prerequisites). For Google sign-in and GCS image uploads:

   ```bash
   gcloud auth application-default login
   gcloud auth application-default set-quota-project milestone2-tbd-cad
   ```

2. **Configure secrets:**

   ```bash
   cd docs/gettingstarted
   cp .env.example .env
   # Edit .env: JWT_SECRET must be ≥ 32 characters
   ```

3. **Full setup** (from `backend/`):

   ```bash
   ./scripts/local-dev.sh setup
   ./scripts/local-dev.sh port-forward
   ```

4. **Frontend** (separate terminal, from repo `frontend/`):

   ```bash
   VITE_API_BASE_URL=http://localhost:8080 npm run dev
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

Designed for a **single Minikube** cluster with **24 GiB RAM** default (`MINIKUBE_MEMORY=24576`) so Elasticsearch, Redis, and three Spring Boot services can start reliably. CPU/memory per pod: [ms2 resource profile](../../../infrastructure/ms2/docs/resource-profile-dev.md) (aligned with GKE gitops).

| Included | Excluded (use GKE guide instead) |
|----------|----------------------------------|
| Minikube cluster (`tripplanning` namespace) | Terraform / VPC / GKE |
| trip + social + external-info (local images `:local`) | Cloud SQL (trip uses **H2** in-pod) |
| In-cluster **Redis** + **Elasticsearch** | GKE Gateway, DNS, TLS, cert-manager |
| In-cluster **Firestore emulator** | Real Firestore `tbd-firestore` |
| H2 file DB in trip-service pod (`emptyDir`) | Artifact Registry push |
| **GCP Identity Platform** (optional Google sign-in) | Frontend on GCS |
| **GCS images bucket** (signed uploads via ADC) | Flux, kube-prometheus |

**Cloud dependencies kept by design:** Identity Platform / Firebase for Google sign-in; GCS for trip/profile image uploads. **dev-login** works without Google when `local` profile is active.

---

## What you will run

| Piece | Technology |
|-------|------------|
| Cluster | [Minikube](https://minikube.sigs.k8s.io/) (driver default: `docker`) |
| Trip API | Spring Boot + H2 + in-cluster Elasticsearch + Redis |
| Social API | Spring Boot + in-cluster Firestore emulator |
| External-info | Spring Boot + Redis + external HTTP APIs |
| Ingress | nginx (minikube addon) — path routing to trip / social / external-info |
| Frontend | Vite dev server → `http://localhost:8080` |

---

## Identity: Google Cloud (optional)

Google sign-in uses the same **Firebase / Identity Platform** project as the GKE stack. Full manual setup: [ms2 Identity section](../../../infrastructure/ms2/docs/gettingstarted/README.md#identity-google-manual).

**Minimum for local Google sign-in:**

1. OAuth Web client with **`http://localhost:5173`** in authorized JavaScript origins.
2. Frontend `VITE_FIREBASE_*` from Firebase Console → Project settings → Web app.
3. `TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID` in `.env` matches that project (default: `milestone2-tbd-cad`).
4. `gcloud auth application-default login` on the machine running Minikube (trip-service validates ID tokens via ADC).

**Without Google:** use **dev-login** ([§9](#9-auth-flows)).

---

## 0. Prerequisites

| Tool | Purpose |
|------|---------|
| [Minikube](https://minikube.sigs.k8s.io/docs/start/) | Local Kubernetes |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | Cluster access |
| [Docker](https://docs.docker.com/get-docker/) | Minikube driver + image build |
| Java 21 + Maven 3.9+ | Backend build |
| [gcloud CLI](https://cloud.google.com/sdk/docs/install) | ADC for Identity Platform + GCS |
| Node.js 20+ | Frontend dev server (optional) |
| `curl` | Verify script smoke tests |

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project milestone2-tbd-cad
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
| `INTERNAL_SECRET` | No | Default `dev-internal-service-secret`; social `/internal/**` |
| `GOOGLE_PROJECT` | No | Default `milestone2-tbd-cad` |
| `TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID` | No | Defaults to `GOOGLE_PROJECT` |
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
| `trip-service-secrets` | `TRIPPLANNING_AUTH_JWT_SECRET` |
| `social-service-secrets` | `TRIPPLANNING_AUTH_JWT_SECRET`, `TRIPPLANNING_INTERNAL_SECRET` |
| `external-info-service-secrets` | `VIATOR_API_KEY` (optional) |

Re-apply after changing `.env`: `./scripts/local-dev.sh deploy`.

---

## 5. Deploy

Builds JARs, images in the Minikube Docker daemon, installs Redis + Elasticsearch, applies `k8s/local` (apps + firestore-emulator):

```bash
./scripts/local-dev.sh deploy
```

Steps inside `deploy`:

1. `mvn package` (trip, social, external-info modules)
2. `docker build` with `imagePullPolicy: Never` tags `tripplanning-*-service:local`
3. [`install-k8s-dependencies.sh`](../../../infrastructure/ms2/scripts/install-k8s-dependencies.sh) — Redis + Elasticsearch in `tripplanning`
4. `kubectl apply -k k8s/local`
5. ConfigMaps + rollout restart

**First trip-service start** can take 1–3 minutes (Elasticsearch index warmup).

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

**Routing:** The SPA uses **one** origin (`http://localhost:8080` or Vite proxy). **Ingress** routes social paths (`/api/v2/comments`, trip community, likes) and `/api/v2/external/*` to the correct service (same path table as GKE Gateway in [`httproute-api.yaml`](../../../infrastructure/ms2/gitops/tenants/tripplanning/gateway/httproute-api.yaml)).

**Logs:**

```bash
./scripts/local-dev.sh logs trip-service
./scripts/local-dev.sh status
```

---

## 8. Frontend

From the `frontend/` directory (with `./scripts/local-dev.sh port-forward` running):

```bash
cp .env.example .env   # set VITE_FIREBASE_* for Google sign-in (optional for dev-login)
npm run dev:minikube
```

Open `http://localhost:5173`. Vite proxies `/api/v2` to `http://localhost:8080` (ingress). CORS is configured on trip-, social-, and external-info-service for `http://localhost:5173` and `http://127.0.0.1:5173`.

For the **GKE** API instead: `npm run dev:k8s` (proxies to `https://api.k8s.tbd-htwg.de`).

---

## 9. Auth flows

### Dev login (no Google)

With `SPRING_PROFILES_ACTIVE=local,k8s` on trip-service:

```bash
curl -sS -X POST http://localhost:8080/api/v2/auth/dev-login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@local.test","name":"Dev User"}'
```

Returns `accessToken` for `Authorization: Bearer …` on `/api/v2/*`.

### Google sign-in

1. Configure Identity Platform / Firebase ([ms2 guide](../../../infrastructure/ms2/docs/gettingstarted/README.md#identity-google-manual)).
2. Frontend obtains Firebase ID token → `POST /api/v2/auth/google` on trip-service.
3. Requires **Application Default Credentials** on the host (Minikube pods use the node's credential chain for GCP client libraries where configured).

---

## 10. GCS images

Trip-service uses the **ms2 dev images bucket** and signer SA (same as GKE), configured via ConfigMap / [`application-local.yml`](../../tripplanning-trip-service/src/main/resources/application-local.yml):

| Setting | Default (ms2 dev) |
|---------|-------------------|
| Bucket | `milestone2-tbd-cad-images-bucket` |
| Signer SA | `tripplanning-image-url-sig@milestone2-tbd-cad.iam.gserviceaccount.com` |

Override in `.env` with `GCP_STORAGE_BUCKET_NAME` and `GCP_IMPERSONATE_SERVICE_ACCOUNT` if your project differs.

### One-time setup (image uploads)

1. **Terraform** must have created the bucket and signer SA (`dev-lifecycle.sh terraform-apply`).

2. **Application Default Credentials** on your machine:

   ```bash
   gcloud auth application-default login
   gcloud auth application-default set-quota-project milestone2-tbd-cad
   ```

   `local-dev.sh deploy` syncs `~/.config/gcloud/application_default_credentials.json` into Kubernetes secret **`gcp-adc`** and mounts it into the trip-service pod.

3. **Impersonation** — your user must be allowed to mint tokens for the signer SA (Terraform grants this to the GKE workload SA only). If signed URLs fail with impersonation errors, run once:

   ```bash
   gcloud iam service-accounts add-iam-policy-binding \
     tripplanning-image-url-sig@milestone2-tbd-cad.iam.gserviceaccount.com \
     --member="user:$(gcloud config get-value account)" \
     --role="roles/iam.serviceAccountTokenCreator" \
     --project="milestone2-tbd-cad"
   ```

   Or use `./scripts/local-dev.sh setup-gcs`, which prints the same hint after applying CORS.

4. **Bucket CORS** (browser PUT from `localhost:5173`):

   ```bash
   ./scripts/local-dev.sh setup-gcs
   ```

**Upload flow:** trip-service returns a signed URL; the browser **PUTs** directly to GCS (not through port-forward).

Trips **without** images work without steps 3–4.

### Firestore composite index (GKE / real Firestore only)

On **ms2 GKE deploy**, `dev-lifecycle.sh setup` runs **`firestore-indexes`** automatically (unless `SKIP_FIRESTORE_INDEXES=true`). It creates a composite index on the **`comments`** collection group in database **`tbd-firestore`**:

- `tripId` (ascending)
- `createdAt` (descending)

That index is **required** for paginated comment lists on **production Firestore**. It is **not** created by `local-dev.sh`.

**Minikube** uses the **in-cluster Firestore emulator**, which typically does **not** enforce composite indexes — you usually do **not** need to create the index locally. Comment **create** (`POST /api/v2/comments`) never needs the index.

To create the index manually on GCP (e.g. after using real Firestore):

```bash
cd infrastructure/ms2/docs/gettingstarted
./dev-lifecycle.sh firestore-indexes
```

---

## 11. Switch to GKE

Before running ms2 cloud deploy, restore GKE kubectl context (stops Minikube by default):

```bash
./scripts/local-dev.sh use-gke
cd ../infrastructure/ms2/docs/gettingstarted
./dev-lifecycle.sh deploy
```

`dev-lifecycle.sh` calls `local-dev.sh use-gke` automatically if you were on Minikube.

---

## `local-dev.sh` command reference

| Command | README § | Description |
|---------|----------|-------------|
| `setup` | §3–§6 | `use-local` + secrets + `deploy` + `verify` |
| `use-local` | §3 | Start minikube, set context |
| `use-gke` | §11 | Restore GKE credentials |
| `deploy` | §5 | Build + kubectl apply |
| `verify` | §6 | Health smoke tests |
| `setup-gcs` | §10 | Apply GCS bucket CORS + impersonation hint |
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
| ☐ `.env` configured | `cp docs/gettingstarted/.env.example docs/gettingstarted/.env` |
| ☐ Cluster + deploy | `./scripts/local-dev.sh setup` |
| ☐ Verify | `./scripts/local-dev.sh verify` |
| ☐ Port-forward | `./scripts/local-dev.sh port-forward` |
| ☐ Frontend | `VITE_API_BASE_URL=http://localhost:8080` → `npm run dev` |
| ☐ Dev login or Google | [§9](#9-auth-flows) |
| ☐ GCS (optional) | ADC login + `./scripts/local-dev.sh setup-gcs` + impersonation binding [§10](#10-gcs-images) |

---

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| **trip-service CrashLoop / not Ready** | Elasticsearch still starting — `kubectl logs -n tripplanning deployment/trip-service --tail=80`. Raise `MINIKUBE_MEMORY`. |
| **HSEARCH400075 / Lucene analysis configurer** | Rebuild image after fix: `local,k8s` must not load Lucene search config (see `application-local-lucene.yml`). Run `./scripts/local-dev.sh deploy`. |
| **500 on `/trips/*/community`** | trip-service called `localhost:8081` from inside the pod — rebuild/deploy after `application-local-k8s-services.yml` fix. Check `kubectl logs deployment/trip-service`. |
| **404 on `POST /api/v2/comments`** | Ingress must route `/api/v2/comments` to social-service — check `kubectl get ingress -n tripplanning` and rebuild/deploy. |
| **Comment list 500 / index errors** | Firestore emulator usually needs no manual DB; composite indexes are required on **real** Firestore (ms2 `dev-lifecycle.sh firestore-indexes`). Emulator often works without them. |
| **500 on trip child resources** (`/accommodations`, etc.) | Elasticsearch pod OOM/crash — `kubectl get pods -l app.kubernetes.io/name=elasticsearch`. Re-apply deps: `NS=tripplanning infrastructure/ms2/scripts/install-k8s-dependencies.sh` then restart trip-service. |
| **social-service Firestore errors** | `kubectl logs -n tripplanning deployment/social-service`; ensure `firestore-emulator` pod is Running. |
| **ImagePullBackOff** | Images must be built **inside** minikube Docker (`eval "$(minikube docker-env)"` is done by `deploy`). Tag must be `:local` with `imagePullPolicy: Never`. |
| **dev-login 404** | Trip must use `SPRING_PROFILES_ACTIVE=local,k8s` (ConfigMap from `local-dev.sh`). |
| **Google sign-in fails** | ADC, OAuth origins, `VITE_FIREBASE_*`, test users on OAuth consent screen. |
| **GCS upload 401 / Anonymous caller** | Run `gcloud auth application-default login`, then `./scripts/local-dev.sh deploy` (syncs `gcp-adc` secret). |
| **signBlob / impersonation denied** | Grant yourself `roles/iam.serviceAccountTokenCreator` on `tripplanning-image-url-sig@…` — see [§10](#10-gcs-images). |
| **Browser PUT blocked by CORS** | `./scripts/local-dev.sh setup-gcs` |
| **OOM / ES evicted** | Increase `MINIKUBE_MEMORY` (e.g. `32768`). |
| **Host vs in-cluster Firestore** | Default in-cluster `firestore-emulator:8080`. Host fallback: `USE_HOST_FIRESTORE_EMULATOR=true`. |

**Recovery after partial deploy:**

```bash
./scripts/local-dev.sh deploy
./scripts/local-dev.sh verify
```

**Compare with GKE:** [ms2 STATE.md](../../../infrastructure/ms2/docs/gettingstarted/STATE.md) · [ms2 README](../../../infrastructure/ms2/docs/gettingstarted/README.md)
