# tripplanning-seed-job

One-shot Spring Boot job that seeds the **performance dataset** (5000 users, 15000 trips, Firestore likes/comments) by writing directly to **PostgreSQL** and **Firestore**. Inputs are **bundled CSV/JSON** on the classpath; sample images are **not uploaded** (only `imagePath` keys referencing `gs://…/sample/…`).

## Bundled assets (`src/main/resources/seed/`)

| File | Source |
|------|--------|
| `dataset-spec.json` | Hand-maintained counts and fractions |
| `sample-images.csv` | [`scripts/extract-seed-assets.py`](../scripts/extract-seed-assets.py) from `_sample_images/` |
| `google-places.json` | [`scripts/fetch-google-places.py`](../scripts/fetch-google-places.py) (or `--synthetic` for offline) |

Regenerate assets after changing sample images or place lists:

```bash
python3 backend/scripts/extract-seed-assets.py
# Terminal 1: ./scripts/local-dev.sh port-forward
# Terminal 2: kubectl port-forward -n tripplanning svc/external-info-service 8082:8082
python3 backend/scripts/fetch-google-places.py --test --api-base http://localhost:8080
python3 backend/scripts/fetch-google-places.py --api-base http://localhost:8080   # or --synthetic
```

## Run locally (Maven)

```bash
cd backend
TRIPPLANNING_SEED_ENABLED=true \
TRIPPLANNING_SEED_ALLOW_NON_EMPTY_DB=true \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tripplanning \
SPRING_DATASOURCE_USERNAME=tripplanning \
SPRING_DATASOURCE_PASSWORD=tripplanning \
SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_ENABLED=true \
SPRING_CLOUD_GCP_FIRESTORE_HOST_PORT=localhost:9090 \
mvn -pl tripplanning-seed-job spring-boot:run
```

## Run on Minikube

Prerequisites: cluster up (`./scripts/local-dev.sh setup`), sample images in GCS (`./scripts/local-dev.sh sync-sample-images`).

```bash
./scripts/local-dev.sh seed-job
```

Each run **wipes and recreates** PostgreSQL (Flyway schema) and Firestore (`comments`, `likes`). GCS sample images under `gs://…/sample/` are **not** deleted.

Writes `performance/seeding_example/perf_seed_manifest.json` for Locust (`PERF_USER_ID_MIN`/`MAX`, per-user `tripIds`).

Disable wipe (e.g. tests): `TRIPPLANNING_SEED_WIPE_BEFORE_SEED=false`.

## Run on GKE dev (`tripplanning-free`)

Prerequisites:

1. Cluster credentials: `gcloud container clusters get-credentials tripplanning-gke --region europe-west1 --project tbd-cloudappdev`
2. Seed-job image published: GitHub Actions → **Docker GKE services** (includes `tripplanning-seed-job`), or `./scripts/gke-seed-job.sh --build-push`
3. Sample images in GKE bucket (one-time or on each full run):

```bash
./scripts/gke-sync-sample-images.sh
# or: ./scripts/sync-sample-images.sh --target prod
```

Wipe + seed (syncs sample images unless `--skip-sync`):

```bash
./scripts/gke-seed-job.sh
# or, if images already synced:
./scripts/gke-seed-job.sh --skip-sync --yes
```

Writes `performance/seeding_example/perf_seed_manifest.json` for Locust. Uses real Firestore (`tbd-firestore`), not the emulator.

See also [GKE dev checklist](../../infrastructure/ms2/docs/gke-dev-hpa-and-test-bearer-checklist.md).

## Ownership guarantees

For every user id **1..5000**:

- ≥1 trip with `trips.user_id = userId`
- ≥1 Firestore comment with `userId` (on own trip)
- ≥1 Firestore like document `{userId}_{tripId}`

Locust continues to use **`X-Act-As-User`** with the shared test bearer for runtime writes only.
