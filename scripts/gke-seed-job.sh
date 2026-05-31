#!/usr/bin/env bash
# One-shot perf seed on GKE dev (tripplanning-free): wipe PostgreSQL + Firestore, run seed job,
# copy perf_seed_manifest.json for Locust.
#
# Usage:
#   ./scripts/gke-seed-job.sh [--skip-sync] [--build-push] [--tag TAG] [--yes] [--skip-search-reset]
#
# Prerequisites:
#   - kubectl context: tripplanning-gke
#   - ghcr.io image published (Docker GKE services workflow) unless --build-push
#   - gcloud ADC for sample image sync (unless --skip-sync)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"
MS2_CHART="${REPO_ROOT}/infrastructure/ms2/charts/tripplanning"
TENANT_VALUES_CONFIGMAP="${REPO_ROOT}/infrastructure/ms2/gitops/tenants/free/shared/values-configmap.yaml"
HELMRELEASE_VALUES="${REPO_ROOT}/infrastructure/ms2/gitops/tenants/free/shared/helmrelease.yaml"
MANIFEST_DEST="${REPO_ROOT}/performance/seeding_example/perf_seed_manifest.json"

GOOGLE_PROJECT="${GOOGLE_PROJECT:-tbd-cloudappdev}"
GOOGLE_REGION="${GOOGLE_REGION:-europe-west1}"
GKE_CLUSTER="${GKE_CLUSTER:-tripplanning-gke}"
NS="${GKE_NAMESPACE:-tripplanning-free}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
GHCR_REGISTRY="${GHCR_REGISTRY:-ghcr.io}"
GHCR_REPO="${GHCR_REPO:-tbd-htwg/backend}"
SKIP_SYNC=false
BUILD_PUSH=false
ASSUME_YES=false
SKIP_PREFLIGHT=false
PREFLIGHT_ONLY=false
SKIP_SEARCH_RESET=false

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  --skip-sync     Skip gke-sync-sample-images.sh (objects already in GCS)
  --skip-preflight
                  Skip cluster preflight checks (valkey, opensearch, postgres, firestore)
  --preflight-only
                  Run preflight checks only; do not sync, wipe, or seed
  --build-push    Build seed-job image locally and push to GHCR before running
  --tag TAG       Seed job image tag (default: latest)
  --skip-search-reset
                  Skip OpenSearch index wipe + trip-service reindex after seed (not recommended)
  --yes           Skip destructive wipe confirmation prompt
  -h, --help      Show this help

Example:
  ./scripts/gke-seed-job.sh
  ./scripts/gke-seed-job.sh --skip-sync --yes
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-sync) SKIP_SYNC=true ;;
    --skip-preflight) SKIP_PREFLIGHT=true ;;
    --preflight-only) PREFLIGHT_ONLY=true ;;
    --build-push) BUILD_PUSH=true ;;
    --skip-search-reset) SKIP_SEARCH_RESET=true ;;
    --tag) IMAGE_TAG="${2:?--tag requires a value}"; shift ;;
    --yes) ASSUME_YES=true ;;
    -h | --help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

require_cmd() {
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || {
      echo "ERROR: required command not found: $c" >&2
      exit 1
    }
  done
}

extract_tenant_values() {
  local out="$1"
  if [[ ! -f "${TENANT_VALUES_CONFIGMAP}" ]]; then
    echo "ERROR: tenant values ConfigMap not found: ${TENANT_VALUES_CONFIGMAP}" >&2
    exit 1
  fi
  awk '
    /^  values.yaml: \|/ { in_values=1; next }
    in_values && /^  [a-zA-Z0-9_.-]+:/ { exit }
    in_values { sub(/^    /, ""); print }
  ' "${TENANT_VALUES_CONFIGMAP}" >"${out}"
  if [[ ! -s "${out}" ]]; then
    echo "ERROR: failed to extract values.yaml from ${TENANT_VALUES_CONFIGMAP}" >&2
    exit 1
  fi
}

ensure_gke_kubectl_target() {
  require_cmd kubectl gcloud helm
  local ctx
  ctx="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "${ctx}" != *"${GKE_CLUSTER}"* ]]; then
    echo "== kubectl → ${GKE_CLUSTER} (${GOOGLE_REGION}) =="
    gcloud container clusters get-credentials "${GKE_CLUSTER}" \
      --region "${GOOGLE_REGION}" \
      --project "${GOOGLE_PROJECT}"
  fi
  kubectl get namespace "${NS}" >/dev/null 2>&1 || {
    echo "ERROR: namespace ${NS} not found in current cluster context" >&2
    exit 1
  }
}

confirm_destructive_wipe() {
  if [[ "${ASSUME_YES}" == "true" ]]; then
    return 0
  fi
  cat <<EOF
WARNING: This will WIPE and re-seed shared dev data in namespace ${NS}:
  - PostgreSQL (Flyway schema drop/recreate)
  - Firestore collections: comments, likes

GCS sample images are NOT deleted.
EOF
  read -r -p "Type 'yes' to continue: " answer
  if [[ "${answer}" != "yes" ]]; then
    echo "Aborted."
    exit 1
  fi
}

maybe_build_push_image() {
  if [[ "${BUILD_PUSH}" != "true" ]]; then
    return 0
  fi
  require_cmd docker
  local image="${GHCR_REGISTRY}/${GHCR_REPO}/tripplanning-seed-job:${IMAGE_TAG}"
  local cachebust
  cachebust="$(date +%s)"
  echo "== Docker build seed-job → ${image} =="
  (cd "${BACKEND_DIR}" && docker build \
    --build-arg SERVICE=seed-job \
    --build-arg CACHEBUST="${cachebust}" \
    -t "${image}" .)
  echo "== Docker push ${image} =="
  docker push "${image}"
}

run_preflight_checks() {
  if [[ "${SKIP_PREFLIGHT}" == "true" ]]; then
    echo "== Skipping preflight checks (--skip-preflight) =="
    return 0
  fi

  echo "== Preflight: backing services =="
  for component in valkey elasticsearch postgres; do
    kubectl wait --for=condition=ready pod -l "app.kubernetes.io/component=${component}" \
      -n "${NS}" --timeout=120s
  done

  echo "== Preflight: trip-service uses Valkey (not Caffeine) =="
  local redis_host internal debug_json cache_backend
  redis_host="$(kubectl get configmap trip-service-config -n "${NS}" \
    -o jsonpath='{.data.SPRING_DATA_REDIS_HOST}')"
  if [[ "${redis_host}" != "valkey" ]]; then
    echo "ERROR: trip-service-config SPRING_DATA_REDIS_HOST=${redis_host:-<unset>} (expected valkey)" >&2
    exit 1
  fi
  internal="$(kubectl get secret trip-service-secrets -n "${NS}" \
    -o jsonpath='{.data.TRIPPLANNING_INTERNAL_SECRET}' | base64 -d)"
  debug_json="$(kubectl exec -n "${NS}" deployment/trip-service -- \
    curl -sS -H "X-Internal-Secret: ${internal}" http://localhost:8080/internal/debug/redis)"
  cache_backend="$(printf '%s' "${debug_json}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('cacheBackend',''))" 2>/dev/null || true)"
  if [[ "${cache_backend}" != "redis" ]]; then
    echo "ERROR: trip-service cacheBackend=${cache_backend:-unknown} (expected redis)" >&2
    echo "Hint: kubectl rollout restart deployment/trip-service -n ${NS}" >&2
    exit 1
  fi
  echo "   cacheBackend=redis OK"

  echo "== Preflight: OpenSearch / Hibernate Search =="
  local search_json search_ready
  search_json="$(kubectl exec -n "${NS}" deployment/trip-service -- \
    curl -sS -H "X-Internal-Secret: ${internal}" http://localhost:8080/internal/debug/search-index)"
  search_ready="$(printf '%s' "${search_json}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('ready', False))" 2>/dev/null || true)"
  if [[ "${search_ready}" != "True" && "${search_ready}" != "true" ]]; then
    echo "WARN: search index not ready yet: ${search_json}" >&2
    echo "      Seed job can still run; reset-search-index runs after seed by default." >&2
  else
    echo "   search index ready OK"
  fi

  echo "== Preflight: Postgres credentials =="
  local pgpass
  pgpass="$(kubectl get secret trip-service-secrets -n "${NS}" -o jsonpath='{.data.password}' | base64 -d)"
  kubectl run "pg-preflight-${RANDOM}" -n "${NS}" --rm -i --restart=Never \
    --image=postgres:15-alpine \
    --env="PGPASSWORD=${pgpass}" \
    --command -- psql -h postgres -U tripplanning_app -d tripplanning -c 'SELECT 1' >/dev/null
  echo "   postgres OK"

  echo "== Preflight: Firestore (real GCP, Workload Identity) =="
  kubectl get sa default -n "${NS}" -o yaml | grep -q 'iam.gke.io/gcp-service-account' || {
    echo "ERROR: default ServiceAccount missing Workload Identity annotation" >&2
    exit 1
  }
  kubectl run "fs-preflight-${RANDOM}" -n "${NS}" --rm -i --restart=Never \
    --image=google/cloud-sdk:slim --command -- bash -lc \
    'TOKEN=$(curl -sS -H "Metadata-Flavor: Google" "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" | python3 -c "import sys,json; print(json.load(sys.stdin)[\"access_token\"])"); code=$(curl -sS -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "https://firestore.googleapis.com/v1/projects/tbd-cloudappdev/databases/tbd-firestore/documents/comments?pageSize=1"); test "$code" = "200"' >/dev/null
  echo "   firestore API OK"

  echo "== Preflight: GHCR pull secret =="
  kubectl get secret ghcr-pull -n "${NS}" >/dev/null
  echo "   ghcr-pull OK"
  echo "== Preflight passed =="
}

apply_seed_job() {
  local tenant_values helm_args gateway_name gateway_ns api_host
  tenant_values="$(mktemp)"
  trap 'rm -f "${tenant_values}"' RETURN
  extract_tenant_values "${tenant_values}"

  gateway_name="$(awk '/name: tripplanning-gateway/{found=1} found && /name:/{print $2; exit}' "${HELMRELEASE_VALUES}" 2>/dev/null || true)"
  gateway_ns="$(awk '/namespace: gateway-system/{print $2; exit}' "${HELMRELEASE_VALUES}" 2>/dev/null || true)"
  api_host="$(awk '/api:/{print $2; exit}' "${HELMRELEASE_VALUES}" 2>/dev/null || true)"
  gateway_name="${gateway_name:-tripplanning-gateway}"
  gateway_ns="${gateway_ns:-gateway-system}"
  api_host="${api_host:-k8s.tbd-htwg.de}"

  helm_args=(
    template tripplanning-free "${MS2_CHART}"
    -f "${tenant_values}"
    --set "seedJob.enabled=true"
    --set "seedJob.image.tag=${IMAGE_TAG}"
    --set "global.gateway.name=${gateway_name}"
    --set "global.gateway.namespace=${gateway_ns}"
    --set "global.hosts.api=${api_host}"
  )

  echo "== Remove previous seed job (if any) =="
  kubectl delete job tripplanning-seed-job -n "${NS}" --ignore-not-found=true --wait=true 2>/dev/null \
    || kubectl delete job tripplanning-seed-job -n "${NS}" --ignore-not-found=true

  echo "== Apply seed job manifest =="
  helm "${helm_args[@]}" --show-only templates/job-seed.yaml | kubectl apply -n "${NS}" -f -
}

wait_for_seed_job() {
  echo "== Waiting for seed job =="
  kubectl wait --for=condition=complete "job/tripplanning-seed-job" -n "${NS}" --timeout=3600s
  local pod
  pod="$(kubectl get pods -n "${NS}" -l job-name=tripplanning-seed-job -o jsonpath='{.items[0].metadata.name}')"
  kubectl logs -n "${NS}" "${pod}" --tail=80
  mkdir -p "$(dirname "${MANIFEST_DEST}")"
  kubectl cp "${NS}/${pod}:/tmp/perf_seed_manifest.json" "${MANIFEST_DEST}"
  echo "Copied manifest to ${MANIFEST_DEST}"
}

restart_services() {
  echo "== Restart social-service after seed =="
  kubectl rollout restart deployment/social-service -n "${NS}"
  kubectl rollout status deployment/social-service -n "${NS}" --timeout=600s || true

  if [[ "${SKIP_SEARCH_RESET}" == "true" || "${SEED_SKIP_SEARCH_RESET:-false}" == "true" ]]; then
    echo "WARN: --skip-search-reset: restarting trip-service without OpenSearch wipe (index may stay stale)."
    kubectl rollout restart deployment/trip-service -n "${NS}"
    kubectl rollout status deployment/trip-service -n "${NS}" --timeout=600s
    return 0
  fi

  echo "== Reset OpenSearch index (seed inserts bypass Hibernate Search) =="
  SEARCH_RESET_NAMESPACE="${NS}" "${SCRIPT_DIR}/reset-search-index.sh"
}

print_locust_next_steps() {
  cat <<EOF

Seed job complete.

Locust (from performance/):
  set -a && source .env && set +a
  locust -f locustfile.py --host=https://k8s.tbd-htwg.de

Ensure PERF_TEST_BEARER matches TRIPPLANNING_AUTH_TEST_BEARER_TOKEN on trip-service.
Trip/user bounds are loaded from ${MANIFEST_DEST} unless overridden in .env.
EOF
}

main() {
  if [[ "${SKIP_SYNC}" != "true" && "${PREFLIGHT_ONLY}" != "true" ]]; then
    echo "== Sync sample images → gs://tbd-cloudappdev-images-bucket/sample/ =="
    "${SCRIPT_DIR}/gke-sync-sample-images.sh"
  fi

  ensure_gke_kubectl_target
  run_preflight_checks
  if [[ "${PREFLIGHT_ONLY}" == "true" ]]; then
    echo "Preflight-only mode; exiting."
    exit 0
  fi
  confirm_destructive_wipe
  maybe_build_push_image
  apply_seed_job
  wait_for_seed_job
  restart_services
  print_locust_next_steps
}

main
