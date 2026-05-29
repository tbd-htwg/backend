#!/usr/bin/env bash
# Sync _sample_images/ to the GKE dev images bucket (tripplanning-free tenant).
# Wrapper for: sync-sample-images.sh --target prod
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/sync-sample-images.sh" --target prod
