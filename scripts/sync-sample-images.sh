#!/usr/bin/env bash
# Sync local _sample_images/ to gs://tbd-test/sample/ for seed data (object paths: sample/{category}/file.jpg).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOCAL_GETTINGSTARTED="${SCRIPT_DIR}/../docs/gettingstarted"

if [[ -f "${LOCAL_GETTINGSTARTED}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${LOCAL_GETTINGSTARTED}/.env"
  set +a
fi

GOOGLE_PROJECT="${GOOGLE_PROJECT:-tbd-cloudappdev}"
GCP_STORAGE_BUCKET_NAME="${GCP_STORAGE_BUCKET_NAME:-tbd-test}"
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
echo "   Source: ${SOURCE}"
echo "   Dest:   ${DEST} (project ${GOOGLE_PROJECT})"
gcloud storage rsync -r "${SOURCE}" "${DEST}" --project="${GOOGLE_PROJECT}"
echo "Done. Reference in trip DB as imagePath, e.g. sample/tourism/12345_name.jpg"
