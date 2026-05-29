#!/usr/bin/env bash
# Local minikube development for tripplanning backend (H2 + in-cluster Firestore emulator + GCP Identity Platform).
# Switch back to GKE before running infrastructure/ms2/docs/gettingstarted/dev-lifecycle.sh.
#
# Canonical guide: backend/docs/gettingstarted/README.md
#
# Usage:
#   ./scripts/local-dev.sh <command>
#
# Commands:
#   setup         First-time: minikube, secrets, deploy, verify
#   use-local     kubectl → minikube, start cluster
#   use-gke       Restore GKE context for dev-lifecycle.sh (optionally stop minikube)
#   start         use-local + deploy
#   stop          Stop host emulator (if used) and minikube
#   delete        minikube delete + clean .local-dev/pids
#   deploy        Build images in minikube Docker, helm upgrade --install
#   verify        Pod health + actuator smoke checks (§6)
#   setup-gcs     Apply GCS images-bucket CORS for browser uploads (§11)
#   setup-gcs-iam One-time signer SA + bucket IAM + user impersonation (tbd-cloudappdev)
#   sync-sample-images  Rsync _sample_images/ → gs://…/sample/ (manual; not part of deploy)
#   seed-job            Wipe PostgreSQL + Firestore, run tripplanning-seed-job, write perf_seed_manifest.json
#   status        Mode, context, pods
#   port-forward  Forward ingress :8080 (API gateway) or per-service ports for debugging
#   logs [svc]    Tail deployment logs (trip-service|social-service|external-info-service)
#   help
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"
LOCAL_GETTINGSTARTED="${BACKEND_DIR}/docs/gettingstarted"
MS2_GETTINGSTARTED="${REPO_ROOT}/infrastructure/ms2/docs/gettingstarted"
LOCAL_DEV_DIR="${BACKEND_DIR}/.local-dev"
STATE_FILE="${LOCAL_DEV_DIR}/state.env"
PID_DIR="${LOCAL_DEV_DIR}/pids"
LOG_DIR="${LOCAL_DEV_DIR}/logs"
K8S_LOCAL="${BACKEND_DIR}/k8s/local"
K8S_CHART="${K8S_LOCAL}/chart"
K8S_RENDERED="${K8S_LOCAL}/rendered/manifests.yaml"
NS="tripplanning"

MINIKUBE_CPUS="${MINIKUBE_CPUS:-4}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-24576}"
MINIKUBE_DRIVER="${MINIKUBE_DRIVER:-docker}"
MINIKUBE_STOP_ON_USE_GKE="${MINIKUBE_STOP_ON_USE_GKE:-true}"
IMAGE_TAG="${IMAGE_TAG:-local}"
USE_HOST_FIRESTORE_EMULATOR="${USE_HOST_FIRESTORE_EMULATOR:-false}"
FIRESTORE_EMULATOR_HOST_PORT="${FIRESTORE_EMULATOR_HOST_PORT:-0.0.0.0:9090}"

if [[ -f "${LOCAL_GETTINGSTARTED}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${LOCAL_GETTINGSTARTED}/.env"
  set +a
fi
if [[ -f "${BACKEND_DIR}/.env.local" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${BACKEND_DIR}/.env.local"
  set +a
fi
if [[ -f "${MS2_GETTINGSTARTED}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${MS2_GETTINGSTARTED}/.env"
  set +a
fi

GOOGLE_PROJECT="${GOOGLE_PROJECT:-tbd-cloudappdev}"
GOOGLE_REGION="${GOOGLE_REGION:-europe-west1}"
GKE_CLUSTER="${GKE_CLUSTER:-tripplanning-gke}"
JWT_SECRET="${JWT_SECRET:-local-dev-only-change-me-32bytes-min!!}"
INTERNAL_SECRET="${INTERNAL_SECRET:-dev-internal-service-secret}"
VIATOR_API_KEY="${VIATOR_API_KEY:-}"
GOOGLE_MAPS_API_KEY="${GOOGLE_MAPS_API_KEY:-}"
TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID:-${GOOGLE_PROJECT}}"
GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME:-tbd-test}"
GCP_IMPERSONATE_SERVICE_ACCOUNT="${GCP_IMPERSONATE_SERVICE_ACCOUNT:-tripplanning-image-url-sig@${GOOGLE_PROJECT}.iam.gserviceaccount.com}"
ADC_FILE="${ADC_FILE:-${HOME}/.config/gcloud/application_default_credentials.json}"
# Opt-in only: deploy/setup do not rsync _sample_images/ (~2.8 GiB) unless SYNC_SAMPLE_IMAGES=true
SYNC_SAMPLE_IMAGES="${SYNC_SAMPLE_IMAGES:-false}"

firestore_emulator_host_port() {
  if [[ "${USE_HOST_FIRESTORE_EMULATOR}" == "true" ]]; then
    echo "host.minikube.internal:9090"
  else
    echo "firestore-emulator.tripplanning.svc.cluster.local:8080"
  fi
}

require_cmd() {
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || {
      echo "ERROR: required command not found: $c"
      exit 1
    }
  done
}

load_state() {
  MODE=""
  SAVED_KUBECTL_CONTEXT=""
  SAVED_GCLOUD_PROJECT=""
  if [[ -f "${STATE_FILE}" ]]; then
    # shellcheck source=/dev/null
    source "${STATE_FILE}"
  fi
}

save_state() {
  mkdir -p "${LOCAL_DEV_DIR}"
  cat >"${STATE_FILE}" <<EOF
MODE=${MODE}
SAVED_KUBECTL_CONTEXT=${SAVED_KUBECTL_CONTEXT:-}
SAVED_GCLOUD_PROJECT=${SAVED_GCLOUD_PROJECT:-}
EOF
}

ensure_local_kubectl_target() {
  require_cmd minikube kubectl
  load_state
  local ctx
  ctx="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "${MODE}" == "local" && "${ctx}" == "minikube" ]]; then
    return 0
  fi
  echo "== kubectl → minikube (local-dev) =="
  save_cloud_context_once
  if ! minikube status >/dev/null 2>&1; then
    echo "ERROR: minikube is not running. Run: ./scripts/local-dev.sh use-local"
    exit 1
  fi
  minikube start 2>/dev/null || true
  kubectl config use-context minikube
  MODE=local
  save_state
}

save_cloud_context_once() {
  load_state
  if [[ -n "${SAVED_KUBECTL_CONTEXT}" ]]; then
    return 0
  fi
  SAVED_KUBECTL_CONTEXT="$(kubectl config current-context 2>/dev/null || true)"
  SAVED_GCLOUD_PROJECT="$(gcloud config get-value project 2>/dev/null || true)"
  save_state
}

cmd_use_local() {
  require_cmd minikube kubectl docker mvn gcloud
  if ! gcloud auth application-default print-access-token >/dev/null 2>&1; then
    echo "WARN: Application Default Credentials missing. For Identity Platform / GCS run:"
    echo "  gcloud auth application-default login"
    echo "  gcloud auth application-default set-quota-project ${GOOGLE_PROJECT}"
  fi
  save_cloud_context_once
  if ! minikube status >/dev/null 2>&1; then
    echo "== Starting minikube (cpus=${MINIKUBE_CPUS}, memory=${MINIKUBE_MEMORY}Mi) =="
    minikube start \
      --cpus="${MINIKUBE_CPUS}" \
      --memory="${MINIKUBE_MEMORY}" \
      --driver="${MINIKUBE_DRIVER}"
  else
    minikube start 2>/dev/null || true
  fi
  if ! minikube addons list 2>/dev/null | grep -qE 'ingress[^|]*\|[[:space:]]*enabled'; then
    echo "== Enabling minikube ingress addon =="
    minikube addons enable ingress
  fi
  ensure_local_ingress_debug_snippets
  kubectl config use-context minikube
  MODE=local
  save_state
  if [[ "${USE_HOST_FIRESTORE_EMULATOR}" == "true" ]]; then
    start_host_firestore_emulator
  else
    echo "Firestore: in-cluster deployment (firestore-emulator:8080)"
  fi
  echo "Local mode active (kubectl context: minikube)."
}

cmd_use_gke() {
  require_cmd gcloud kubectl
  if [[ "${MINIKUBE_STOP_ON_USE_GKE}" == "true" ]] && minikube status >/dev/null 2>&1; then
    echo "== Stopping minikube =="
    stop_host_firestore_emulator || true
    minikube stop 2>/dev/null || true
  fi
  echo "== Restoring GKE kubectl context =="
  gcloud config set project "${GOOGLE_PROJECT}" >/dev/null
  export USE_GKE_GCLOUD_AUTH_PLUGIN=True
  gcloud container clusters get-credentials "${GKE_CLUSTER}" \
    --region "${GOOGLE_REGION}" \
    --project "${GOOGLE_PROJECT}"
  MODE=gke
  save_state
  echo "GKE mode active. Run cloud deploy from:"
  echo "  cd ${MS2_GETTINGSTARTED} && ./dev-lifecycle.sh deploy"
}

firestore_emulator_installed() {
  gcloud components list 2>/dev/null \
    | grep -E 'cloud-firestore-emulator|Firestore Emulator' \
    | grep -q Installed
}

start_host_firestore_emulator() {
  require_cmd gcloud curl
  if ! firestore_emulator_installed; then
    echo "ERROR: Firestore emulator not installed. Run:"
    echo "  gcloud components install cloud-firestore-emulator"
    exit 1
  fi
  mkdir -p "${PID_DIR}" "${LOG_DIR}"
  local pid_file="${PID_DIR}/firestore-emulator.pid"
  if [[ -f "${pid_file}" ]] && kill -0 "$(cat "${pid_file}")" 2>/dev/null; then
    echo "Firestore emulator already running (pid $(cat "${pid_file}"))."
    return 0
  fi
  echo "== Starting Firestore emulator (host gcloud) on ${FIRESTORE_EMULATOR_HOST_PORT} =="
  gcloud emulators firestore start --host-port="${FIRESTORE_EMULATOR_HOST_PORT}" \
    >"${LOG_DIR}/firestore-emulator.log" 2>&1 &
  echo $! >"${pid_file}"
  local n=0
  while (( n < 45 )); do
    if curl -sf "http://127.0.0.1:9090" >/dev/null 2>&1 \
      || curl -sf "http://127.0.0.1:9090/" >/dev/null 2>&1; then
      echo "Firestore emulator ready (log: ${LOG_DIR}/firestore-emulator.log)"
      return 0
    fi
    sleep 1
    n=$((n + 1))
  done
  echo "WARN: Firestore emulator may still be starting — check ${LOG_DIR}/firestore-emulator.log"
}

sync_gcp_adc_secret() {
  kubectl get namespace "${NS}" >/dev/null 2>&1 || kubectl create namespace "${NS}"
  if [[ ! -f "${ADC_FILE}" ]]; then
    echo "WARN: Application Default Credentials not found: ${ADC_FILE}"
    echo "      GCS signed URLs will fail until you run:"
    echo "        gcloud auth application-default login"
    echo "        gcloud auth application-default set-quota-project ${GOOGLE_PROJECT}"
    kubectl delete secret gcp-adc -n "${NS}" --ignore-not-found 2>/dev/null || true
    return 0
  fi
  echo "== Syncing gcp-adc secret from ${ADC_FILE} =="
  kubectl create secret generic gcp-adc \
    --namespace="${NS}" \
    --from-file=adc.json="${ADC_FILE}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

cmd_setup_gcs() {
  require_cmd gsutil
  local cors_file="${REPO_ROOT}/frontend/doc/image-bucket-cors/cors.json"
  if [[ ! -f "${cors_file}" ]]; then
    echo "ERROR: CORS policy not found: ${cors_file}"
    exit 1
  fi
  if ! gcloud auth application-default print-access-token >/dev/null 2>&1; then
    echo "ERROR: ADC required. Run: gcloud auth application-default login"
    exit 1
  fi
  gcloud config set project "${GOOGLE_PROJECT}" >/dev/null
  echo "== GCS images bucket CORS (browser PUT to signed URLs) =="
  echo "   Bucket: gs://${GCP_STORAGE_BUCKET_NAME}"
  gsutil cors set "${cors_file}" "gs://${GCP_STORAGE_BUCKET_NAME}"
  echo ""
  echo "If uploads fail with signBlob/impersonation errors, run once:"
  echo "  ./scripts/local-dev.sh setup-gcs-iam"
}

cmd_setup_gcs_iam() {
  "${SCRIPT_DIR}/setup-gcs-dev-iam.sh"
}

maybe_sync_sample_images() {
  if [[ "${SYNC_SAMPLE_IMAGES}" == "true" || "${SYNC_SAMPLE_IMAGES}" == "1" ]]; then
    cmd_sync_sample_images
  fi
}

cmd_sync_sample_images() {
  "${SCRIPT_DIR}/sync-sample-images.sh" --target test
}

stop_host_firestore_emulator() {
  local pid_file="${PID_DIR}/firestore-emulator.pid"
  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(cat "${pid_file}")"
    if kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
      pkill -P "${pid}" 2>/dev/null || true
    fi
    rm -f "${pid_file}"
  fi
  pkill -f "gcloud emulators firestore start" 2>/dev/null || true
  pkill -f "cloud-firestore-emulator" 2>/dev/null || true
}

apply_local_configmaps() {
  local fse_host
  fse_host="$(firestore_emulator_host_port)"
  kubectl get namespace "${NS}" >/dev/null 2>&1 || kubectl create namespace "${NS}"
  kubectl create configmap trip-service-config \
    --namespace="${NS}" \
    --from-literal=SPRING_PROFILES_ACTIVE=local,k8s,postgres \
    --from-literal=POSTGRES_HOST=postgres \
    --from-literal=POSTGRES_PORT=5432 \
    --from-literal=POSTGRES_DB=tripplanning \
    --from-literal=POSTGRES_USER=tripplanning \
    --from-literal=POSTGRES_PASSWORD=tripplanning \
    --from-literal=TRIPPLANNING_SOCIAL_SERVICE_URL="http://social-service.tripplanning.svc.cluster.local:8081" \
    --from-literal=TRIPPLANNING_EXTERNAL_INFO_SERVICE_URL="http://external-info-service.tripplanning.svc.cluster.local:8082" \
    --from-literal=TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --from-literal=CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173" \
    --from-literal=ELASTICSEARCH_PROTOCOL=http \
    --from-literal=ELASTICSEARCH_HOSTS=opensearch:9200 \
    --from-literal=HIBERNATE_SEARCH_BACKEND_VERSION=opensearch:2.19 \
    --from-literal=TRIPPLANNING_SEARCH_ELASTICSEARCH_INDEX_NAME=tripentity-local \
    --from-literal=SPRING_DATA_REDIS_HOST=valkey \
    --from-literal=SPRING_DATA_REDIS_PORT=6379 \
    --from-literal=GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME}" \
    --from-literal=GCP_IMPERSONATE_SERVICE_ACCOUNT="${GCP_IMPERSONATE_SERVICE_ACCOUNT}" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl create configmap social-service-config \
    --namespace="${NS}" \
    --from-literal=SPRING_PROFILES_ACTIVE=local \
    --from-literal=SERVER_PORT=8081 \
    --from-literal=CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173" \
    --from-literal=SPRING_CLOUD_GCP_FIRESTORE_HOST_PORT="${fse_host}" \
    --from-literal=SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_ENABLED=true \
    --from-literal=GCP_FIRESTORE_DATABASE_ID="(default)" \
    --from-literal=GOOGLE_CLOUD_PROJECT="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --from-literal=TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --from-literal=TRIPPLANNING_TRIP_SERVICE_URL="http://trip-service.tripplanning.svc.cluster.local:8080" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl create configmap external-info-service-config \
    --namespace="${NS}" \
    --from-literal=SERVER_PORT=8082 \
    --from-literal=CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173" \
    --from-literal=SPRING_DATA_REDIS_HOST=valkey \
    --from-literal=SPRING_DATA_REDIS_PORT=6379 \
    --dry-run=client -o yaml | kubectl apply -f -
}

apply_local_secrets() {
  if [[ ${#JWT_SECRET} -lt 32 ]]; then
    echo "ERROR: JWT_SECRET must be at least 32 characters (set in docs/gettingstarted/.env)"
    exit 1
  fi
  kubectl get namespace "${NS}" >/dev/null 2>&1 || kubectl create namespace "${NS}"
  kubectl create secret generic trip-service-secrets \
    --namespace="${NS}" \
    --from-literal=TRIPPLANNING_AUTH_JWT_SECRET="${JWT_SECRET}" \
    --from-literal=TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl create secret generic social-service-secrets \
    --namespace="${NS}" \
    --from-literal=TRIPPLANNING_AUTH_JWT_SECRET="${JWT_SECRET}" \
    --from-literal=TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl create secret generic external-info-service-secrets \
    --namespace="${NS}" \
    --from-literal=TRIPPLANNING_AUTH_JWT_SECRET="${JWT_SECRET}" \
    --from-literal=TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    --from-literal=VIATOR_API_KEY="${VIATOR_API_KEY}" \
    --from-literal=GOOGLE_MAPS_API_KEY="${GOOGLE_MAPS_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

render_local_manifests() {
  require_cmd helm
  mkdir -p "$(dirname "${K8S_RENDERED}")"
  echo "== helm template (k8s/local/chart) → ${K8S_RENDERED} =="
  helm_local_template_args >"${K8S_RENDERED}"
}

# Shared helm args for template / upgrade (values + dynamic env from .env).
helm_local_template_args() {
  local firestore_enabled="true"
  if [[ "${USE_HOST_FIRESTORE_EMULATOR}" == "true" ]]; then
    firestore_enabled="false"
  fi
  local fse_host
  fse_host="$(firestore_emulator_host_port)"
  helm template tripplanning "${K8S_CHART}" \
    -f "${K8S_CHART}/values.yaml" \
    -f "${K8S_CHART}/values-local.yaml" \
    --namespace "${NS}" \
    --set services.trip.image.tag="${IMAGE_TAG}" \
    --set services.social.image.tag="${IMAGE_TAG}" \
    --set services.externalInfo.image.tag="${IMAGE_TAG}" \
    --set firestoreEmulator.enabled="${firestore_enabled}" \
    --set services.trip.env.TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --set services.trip.env.GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME}" \
    --set services.trip.env.GCP_IMPERSONATE_SERVICE_ACCOUNT="${GCP_IMPERSONATE_SERVICE_ACCOUNT}" \
    --set services.social.env.SPRING_CLOUD_GCP_FIRESTORE_HOST_PORT="${fse_host}" \
    --set services.social.env.SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_ENABLED=true \
    --set services.social.env.GOOGLE_CLOUD_PROJECT="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --set services.social.env.TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    "$@"
}

# Valkey Admin Vite assets require /debug/valkey/ in the browser; Exact redirect cannot live on an
# Ingress (regex rule wins). Use controller server-snippet instead (no per-Ingress snippets needed).
ensure_local_ingress_debug_snippets() {
  require_cmd kubectl
  local ingress_ns="ingress-nginx"
  local cm="ingress-nginx-controller"
  if ! kubectl get cm -n "${ingress_ns}" "${cm}" >/dev/null 2>&1; then
    return 0
  fi
  local snippet='location = /debug/valkey { return 301 /debug/valkey/; }'
  local current
  current="$(kubectl get cm -n "${ingress_ns}" "${cm}" -o jsonpath='{.data.server-snippet}' 2>/dev/null || true)"
  if [[ "${current}" == *"${snippet}"* ]]; then
    return 0
  fi
  echo "== ingress-nginx: redirect /debug/valkey → /debug/valkey/ =="
  # Remove invalid http-snippet from earlier attempts (location is not allowed in http context).
  kubectl patch cm -n "${ingress_ns}" "${cm}" --type json \
    -p='[{"op":"remove","path":"/data/http-snippet"}]' 2>/dev/null || true
  kubectl patch cm -n "${ingress_ns}" "${cm}" --type merge -p "$(python3 -c "
import json, sys
snippet = sys.argv[1]
existing = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] else ''
merged = (existing.rstrip() + '\n' + snippet).strip() if existing else snippet
print(json.dumps({'data': {'server-snippet': merged}}))
" "${snippet}" "${current}")"
  kubectl rollout restart deployment/ingress-nginx-controller -n "${ingress_ns}" >/dev/null 2>&1 || true
  kubectl rollout status deployment/ingress-nginx-controller -n "${ingress_ns}" --timeout=120s >/dev/null 2>&1 || true
}

helm_upgrade_local() {
  require_cmd helm
  ensure_local_ingress_debug_snippets
  echo "== helm dependency build (opensearch + valkey subcharts) =="
  helm dependency build "${K8S_CHART}"
  kubectl get namespace "${NS}" >/dev/null 2>&1 || kubectl create namespace "${NS}"
  local firestore_enabled="true"
  if [[ "${USE_HOST_FIRESTORE_EMULATOR}" == "true" ]]; then
    firestore_enabled="false"
  fi
  local fse_host
  fse_host="$(firestore_emulator_host_port)"
  echo "== helm upgrade --install tripplanning (k8s/local/chart) =="
  # --take-ownership: adopt resources previously applied via kubectl apply / helm template.
  helm upgrade --install tripplanning "${K8S_CHART}" \
    -n "${NS}" \
    --create-namespace \
    --take-ownership \
    -f "${K8S_CHART}/values.yaml" \
    -f "${K8S_CHART}/values-local.yaml" \
    --set services.trip.image.tag="${IMAGE_TAG}" \
    --set services.social.image.tag="${IMAGE_TAG}" \
    --set services.externalInfo.image.tag="${IMAGE_TAG}" \
    --set firestoreEmulator.enabled="${firestore_enabled}" \
    --set services.trip.env.TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --set services.trip.env.GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME}" \
    --set services.trip.env.GCP_IMPERSONATE_SERVICE_ACCOUNT="${GCP_IMPERSONATE_SERVICE_ACCOUNT}" \
    --set services.social.env.SPRING_CLOUD_GCP_FIRESTORE_HOST_PORT="${fse_host}" \
    --set services.social.env.SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_ENABLED=true \
    --set services.social.env.GOOGLE_CLOUD_PROJECT="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    --set services.social.env.TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    "$@"
}

cmd_seed_job() {
  require_cmd mvn docker helm kubectl
  ensure_local_kubectl_target
  apply_local_secrets
  echo "== Maven package (seed-job) =="
  (cd "${BACKEND_DIR}" && mvn -q -pl tripplanning-seed-job -am package -DskipTests)
  echo "== Docker build seed-job =="
  eval "$(minikube docker-env)"
  local cachebust
  cachebust="$(date +%s)"
  (cd "${BACKEND_DIR}" && docker build --build-arg SERVICE=seed-job --build-arg CACHEBUST="${cachebust}" -t "tripplanning-seed-job:${IMAGE_TAG}" .)

  local fse_host
  fse_host="$(firestore_emulator_host_port)"
  echo "== helm upgrade (postgres + apps) =="
  helm_upgrade_local --set backingServices.postgres.enabled=true

  # Job spec.template is immutable — remove any previous run, then apply a fresh Job manifest.
  echo "== Remove previous seed job (if any) =="
  kubectl delete job tripplanning-seed-job -n "${NS}" --ignore-not-found=true --wait=true 2>/dev/null \
    || kubectl delete job tripplanning-seed-job -n "${NS}" --ignore-not-found=true

  echo "== Apply seed job manifest =="
  helm_local_template_args \
    --set backingServices.postgres.enabled=true \
    --set seedJob.enabled=true \
    --set seedJob.image.tag="${IMAGE_TAG}" \
    --set seedJob.firestoreHostPort="${fse_host}" \
    | kubectl apply -n "${NS}" -f -

  echo "Waiting for postgres..."
  kubectl rollout status statefulset/postgres -n "${NS}" --timeout=300s || true
  kubectl wait --for=condition=ready pod -l app.kubernetes.io/component=postgres -n "${NS}" --timeout=300s || true

  echo "Restart trip/social to pick up postgres + config..."
  kubectl rollout restart deployment/trip-service deployment/social-service -n "${NS}" || true
  kubectl rollout status deployment/trip-service -n "${NS}" --timeout=600s || true

  echo "== Waiting for seed job =="
  kubectl wait --for=condition=complete job/tripplanning-seed-job -n "${NS}" --timeout=3600s
  local pod
  pod="$(kubectl get pods -n "${NS}" -l job-name=tripplanning-seed-job -o jsonpath='{.items[0].metadata.name}')"
  kubectl logs -n "${NS}" "${pod}" --tail=80
  local manifest_dest="${REPO_ROOT}/performance/seeding_example/perf_seed_manifest.json"
  kubectl cp "${NS}/${pod}:/tmp/perf_seed_manifest.json" "${manifest_dest}"
  echo "Copied manifest to ${manifest_dest}"

  echo "Restart trip/social after seed (search index + DB connections)..."
  kubectl rollout restart deployment/trip-service deployment/social-service -n "${NS}" || true
  kubectl rollout status deployment/trip-service -n "${NS}" --timeout=600s || true
}

cmd_deploy() {
  require_cmd mvn docker helm
  apply_local_secrets
  echo "== Maven package =="
  (cd "${BACKEND_DIR}" && mvn -q -pl tripplanning-trip-service,tripplanning-social-service,tripplanning-external-info-service,tripplanning-seed-job -am package -DskipTests)
  echo "== Docker build (minikube daemon) =="
  eval "$(minikube docker-env)"
  local cachebust
  cachebust="$(date +%s)"
  (cd "${BACKEND_DIR}" && docker build --build-arg SERVICE=trip --build-arg CACHEBUST="${cachebust}" -t "tripplanning-trip-service:${IMAGE_TAG}" .)
  (cd "${BACKEND_DIR}" && docker build --build-arg SERVICE=social --build-arg CACHEBUST="${cachebust}" -t "tripplanning-social-service:${IMAGE_TAG}" .)
  (cd "${BACKEND_DIR}" && docker build --build-arg SERVICE=external-info --build-arg CACHEBUST="${cachebust}" -t "tripplanning-external-info-service:${IMAGE_TAG}" .)
  (cd "${BACKEND_DIR}" && docker build --build-arg SERVICE=seed-job --build-arg CACHEBUST="${cachebust}" -t "tripplanning-seed-job:${IMAGE_TAG}" .)
  sync_gcp_adc_secret
  maybe_sync_sample_images

  helm_upgrade_local

  render_local_manifests

  echo "== restart app deployments (pick up rebuilt :${IMAGE_TAG} images) =="
  kubectl rollout restart deployment/trip-service deployment/social-service deployment/external-info-service -n "${NS}"

  echo "Waiting for valkey..."
  kubectl wait --for=condition=available deployment/valkey -n "${NS}" --timeout=120s 2>/dev/null \
    || kubectl rollout status deployment/valkey -n "${NS}" --timeout=120s
  echo "Waiting for opensearch..."
  kubectl rollout status statefulset/opensearch -n "${NS}" --timeout=300s || true

  if [[ "${USE_HOST_FIRESTORE_EMULATOR}" != "true" ]]; then
    echo "Waiting for firestore-emulator..."
    kubectl rollout status deployment/firestore-emulator -n "${NS}" --timeout=120s || true
  fi
  echo "Waiting for app rollouts..."
  kubectl rollout status deployment/trip-service -n "${NS}" --timeout=600s || true
  kubectl rollout status deployment/social-service -n "${NS}" --timeout=180s || true
  kubectl rollout status deployment/external-info-service -n "${NS}" --timeout=120s || true
  if [[ "${USE_HOST_FIRESTORE_EMULATOR}" != "true" ]]; then
    kubectl rollout status deployment/valkey-admin -n "${NS}" --timeout=120s 2>/dev/null || true
    kubectl rollout status deployment/opensearch-dashboards -n "${NS}" --timeout=180s 2>/dev/null || true
  fi
  kubectl get pods -n "${NS}"
}

cmd_verify() {
  NS="${NS}" "${SCRIPT_DIR}/verify-local-deployment.sh" "$@"
}

cmd_setup() {
  cmd_use_local
  apply_local_secrets
  cmd_deploy
  echo ""
  cmd_verify || true
  echo ""
  echo "== Local minikube ready =="
  echo "  Guide: ${LOCAL_GETTINGSTARTED}/README.md"
  echo "  ./scripts/local-dev.sh port-forward   # ingress → localhost:8080"
  echo "  Frontend: cd ../frontend && npm run dev:minikube"
  echo "  Image uploads: ./scripts/local-dev.sh setup-gcs-iam && setup-gcs  (once, after ADC login)"
  echo "  Sample images: ./scripts/local-dev.sh sync-sample-images  (optional; skipped on deploy by default)"
  echo "  Perf dataset:    ./scripts/local-dev.sh seed-job  (PostgreSQL + Firestore; needs sync-sample-images first)"
  echo "  Return to GKE: ./scripts/local-dev.sh use-gke"
}

cmd_start() {
  cmd_use_local
  cmd_deploy
}

cmd_stop() {
  stop_host_firestore_emulator
  minikube stop 2>/dev/null || true
  echo "Stopped minikube."
}

cmd_delete() {
  stop_host_firestore_emulator
  minikube delete 2>/dev/null || true
  rm -rf "${PID_DIR}"
  echo "Minikube deleted."
}

cmd_status() {
  load_state
  echo "MODE: ${MODE:-unset}"
  echo "kubectl context: $(kubectl config current-context 2>/dev/null || echo n/a)"
  echo "Firestore: $(firestore_emulator_host_port) (USE_HOST_FIRESTORE_EMULATOR=${USE_HOST_FIRESTORE_EMULATOR})"
  echo "GCS bucket: ${GCP_STORAGE_BUCKET_NAME}"
  echo "GCS signer: ${GCP_IMPERSONATE_SERVICE_ACCOUNT}"
  if [[ -f "${ADC_FILE}" ]]; then
    echo "ADC file: ${ADC_FILE} (synced to secret gcp-adc)"
  else
    echo "ADC file: missing (${ADC_FILE})"
  fi
  minikube status 2>/dev/null || echo "minikube: not running"
  if [[ -f "${PID_DIR}/firestore-emulator.pid" ]]; then
    echo "Host Firestore emulator pid: $(cat "${PID_DIR}/firestore-emulator.pid")"
  fi
  kubectl get pods -n "${NS}" 2>/dev/null || true
}

cmd_port_forward() {
  if ! kubectl get namespace "${NS}" >/dev/null 2>&1; then
    echo "ERROR: namespace ${NS} not found. Run: ./scripts/local-dev.sh setup"
    exit 1
  fi
  local ingress_ns="ingress-nginx"
  if kubectl get svc -n "${ingress_ns}" ingress-nginx-controller >/dev/null 2>&1; then
    echo "Forwarding ingress → localhost:8080 (all API paths; Ctrl+C to stop)"
    kubectl port-forward -n "${ingress_ns}" svc/ingress-nginx-controller 8080:80
    return
  fi
  echo "WARN: ingress-nginx not found; forwarding individual services"
  kubectl port-forward -n "${NS}" svc/trip-service 8080:8080 &
  kubectl port-forward -n "${NS}" svc/social-service 8081:8081 &
  kubectl port-forward -n "${NS}" svc/external-info-service 8082:8082 &
  wait
}

cmd_logs() {
  local dep="${1:-trip-service}"
  kubectl logs -n "${NS}" "deployment/${dep}" -f --tail=100
}

usage() {
  sed -n '6,23p' "$0" | sed 's/^# \{0,1\}//'
}

main() {
  local cmd="${1:-help}"
  shift || true
  case "${cmd}" in
    use-gke) cmd_use_gke ;;
    sync-sample-images) cmd_sync_sample_images ;;
    seed-job) cmd_seed_job ;;
    help|-h|--help) usage ;;
    *)
      ensure_local_kubectl_target
      case "${cmd}" in
        setup) cmd_setup ;;
        use-local) cmd_use_local ;;
        start) cmd_start ;;
        stop) cmd_stop ;;
        delete) cmd_delete ;;
        deploy) cmd_deploy ;;
        verify) cmd_verify "$@" ;;
        setup-gcs|bucket-cors) cmd_setup_gcs ;;
        setup-gcs-iam) cmd_setup_gcs_iam ;;
        status) cmd_status ;;
        port-forward) cmd_port_forward ;;
        logs) cmd_logs "$@" ;;
        *)
          echo "Unknown command: ${cmd}"
          usage
          exit 1
          ;;
      esac
      ;;
  esac
}

main "$@"
