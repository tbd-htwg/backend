#!/usr/bin/env bash
# Merge _sample_images/*/downloaded_content.csv into seed-job bundled sample-images.csv.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${SCRIPT_DIR}/extract-seed-assets.py" "$@"
