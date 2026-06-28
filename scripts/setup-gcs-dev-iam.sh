#!/usr/bin/env bash
# One-time GCP IAM for local Minikube GCS signed uploads (project tbd-cloudappdev by default).
# Run from backend/: ./scripts/setup-gcs-dev-iam.sh
# Requires: gcloud ADC login, permission to manage IAM on the dev project.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOCAL_GETTINGSTARTED="${BACKEND_DIR}/docs/gettingstarted"

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

GOOGLE_PROJECT="${GOOGLE_PROJECT:-tbd-cloudappdev}"
GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME:-tbd-cloudappdev-images-bucket}"
GCP_IMPERSONATE_SERVICE_ACCOUNT="${GCP_IMPERSONATE_SERVICE_ACCOUNT:-tripplanning-image-url-sig@${GOOGLE_PROJECT}.iam.gserviceaccount.com}"
SA_ACCOUNT_ID="${SA_ACCOUNT_ID:-tripplanning-image-url-sig}"

require_cmd() {
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || {
      echo "ERROR: required command not found: $c"
      exit 1
    }
  done
}

require_cmd gcloud gsutil

if ! gcloud auth application-default print-access-token >/dev/null 2>&1; then
  echo "ERROR: Application Default Credentials required."
  echo "  gcloud auth application-default login"
  echo "  gcloud auth application-default set-quota-project ${GOOGLE_PROJECT}"
  exit 1
fi

USER_EMAIL="$(gcloud config get-value account 2>/dev/null || true)"
if [[ -z "${USER_EMAIL}" || "${USER_EMAIL}" == "(unset)" ]]; then
  echo "ERROR: No gcloud user account. Run: gcloud auth login"
  exit 1
fi

gcloud config set project "${GOOGLE_PROJECT}" >/dev/null
echo "== GCS dev IAM (project=${GOOGLE_PROJECT}, bucket=${GCP_STORAGE_BUCKET_NAME}) =="
echo "   Signer SA: ${GCP_IMPERSONATE_SERVICE_ACCOUNT}"
echo "   Your user: ${USER_EMAIL}"
echo ""

if ! gcloud storage buckets describe "gs://${GCP_STORAGE_BUCKET_NAME}" --project="${GOOGLE_PROJECT}" >/dev/null 2>&1; then
  echo "ERROR: Bucket gs://${GCP_STORAGE_BUCKET_NAME} not found in ${GOOGLE_PROJECT}."
  exit 1
fi

if ! gcloud iam service-accounts describe "${GCP_IMPERSONATE_SERVICE_ACCOUNT}" --project="${GOOGLE_PROJECT}" >/dev/null 2>&1; then
  echo "== Creating service account ${SA_ACCOUNT_ID} =="
  gcloud iam service-accounts create "${SA_ACCOUNT_ID}" \
    --project="${GOOGLE_PROJECT}" \
    --display-name="Tripplanning signed GCS upload URL signer"
else
  echo "== Service account already exists =="
fi

echo "== Bucket IAM: objectViewer + objectCreator for signer SA =="
for role in roles/storage.objectViewer roles/storage.objectCreator; do
  gcloud storage buckets add-iam-policy-binding "gs://${GCP_STORAGE_BUCKET_NAME}" \
    --project="${GOOGLE_PROJECT}" \
    --member="serviceAccount:${GCP_IMPERSONATE_SERVICE_ACCOUNT}" \
    --role="${role}" \
    --quiet >/dev/null 2>&1 || true
done

echo "== Allow your user to impersonate signer SA (Minikube ADC → signBlob) =="
gcloud iam service-accounts add-iam-policy-binding "${GCP_IMPERSONATE_SERVICE_ACCOUNT}" \
  --project="${GOOGLE_PROJECT}" \
  --member="user:${USER_EMAIL}" \
  --role="roles/iam.serviceAccountTokenCreator" \
  --quiet

echo ""
echo "Done. Next:"
echo "  gcloud auth application-default set-quota-project ${GOOGLE_PROJECT}"
echo "  ./scripts/local-dev.sh setup-gcs    # CORS on gs://${GCP_STORAGE_BUCKET_NAME}"
echo "  ./scripts/local-dev.sh deploy       # sync gcp-adc secret + ConfigMap"
