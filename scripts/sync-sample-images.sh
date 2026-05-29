#!/usr/bin/env bash
# Sync local _sample_images/ to a GCS bucket under sample/ for seed data.
#
# Usage:
#   ./scripts/sync-sample-images.sh [--target test|prod]
#
# Targets:
#   test (default)  → gs://tbd-test/sample/                         (Minikube/local dev)
#   prod            → gs://tbd-cloudappdev-images-bucket/sample/    (GKE tripplanning-free)
#
# Explicit --target always wins over docs/gettingstarted/.env (avoids accidental test-bucket sync on GKE).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOCAL_GETTINGSTARTED="${SCRIPT_DIR}/../docs/gettingstarted"

BUCKET_TEST="tbd-test"
BUCKET_PROD="tbd-cloudappdev-images-bucket"
TARGET=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [--target test|prod]

  --target test   Sync to gs://${BUCKET_TEST}/sample/ (Minikube/local; default)
  --target prod   Sync to gs://${BUCKET_PROD}/sample/ (GKE dev / tripplanning-free)

Environment overrides (optional, ignored when --target is set):
  GOOGLE_PROJECT, GCP_STORAGE_BUCKET_NAME, GCS_SAMPLE_PREFIX, SAMPLE_IMAGES_DIR
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      TARGET="${2:?--target requires test or prod}"
      shift 2
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -n "${TARGET}" ]]; then
  case "${TARGET}" in
    test) GCP_STORAGE_BUCKET_NAME="${BUCKET_TEST}" ;;
    prod) GCP_STORAGE_BUCKET_NAME="${BUCKET_PROD}" ;;
    *)
      echo "ERROR: --target must be 'test' or 'prod', got: ${TARGET}" >&2
      exit 1
      ;;
  esac
  GOOGLE_PROJECT="${GOOGLE_PROJECT:-tbd-cloudappdev}"
else
  if [[ -f "${LOCAL_GETTINGSTARTED}/.env" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${LOCAL_GETTINGSTARTED}/.env"
    set +a
  fi
  GOOGLE_PROJECT="${GOOGLE_PROJECT:-tbd-cloudappdev}"
  GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME:-${BUCKET_TEST}}"
fi

GCS_PREFIX="${GCS_SAMPLE_PREFIX:-sample}"
SOURCE="${SAMPLE_IMAGES_DIR:-${REPO_ROOT}/_sample_images}"

command -v gcloud >/dev/null 2>&1 || {
  echo "ERROR: gcloud not found"
  exit 1
}

if [[ ! -d "${SOURCE}" ]]; then
  echo "ERROR: source directory not found: ${SOURCE}"
  exit 1
fi

DEST="gs://${GCP_STORAGE_BUCKET_NAME}/${GCS_PREFIX}"
echo "== gcloud storage rsync =="
echo "   Target: ${TARGET:-env/default}"
echo "   Source: ${SOURCE}"
echo "   Dest:   ${DEST} (project ${GOOGLE_PROJECT})"
gcloud storage rsync -r "${SOURCE}" "${DEST}" --project="${GOOGLE_PROJECT}"
echo "Done. Reference in trip DB as imagePath, e.g. sample/tourism/12345_name.jpg"
