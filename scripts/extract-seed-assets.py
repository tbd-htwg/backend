#!/usr/bin/env python3
"""Merge _sample_images/*/downloaded_content.csv → tripplanning-seed-job sample-images.csv."""
from __future__ import annotations

import csv
import os
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = SCRIPT_DIR.parent
REPO_ROOT = BACKEND_DIR.parent
SOURCE = Path(os.environ.get("SAMPLE_IMAGES_DIR", REPO_ROOT / "_sample_images"))
PREFIX = os.environ.get("GCS_SAMPLE_PREFIX", "sample")
OUT = BACKEND_DIR / "tripplanning-seed-job" / "src" / "main" / "resources" / "seed" / "sample-images.csv"
TEST_OUT = BACKEND_DIR / "tripplanning-seed-job" / "src" / "test" / "resources" / "seed" / "sample-images.csv"
REGIONS_SIDECAR = (
    BACKEND_DIR / "tripplanning-seed-job" / "src" / "main" / "resources" / "seed" / "sample-image-regions.csv"
)

# Bootstrap region tags when no sidecar entry exists.
CATEGORY_REGION_DEFAULTS: dict[str, str] = {
    "castle": "europe",
    "oldtown": "europe",
    "lightrail": "europe",
    "railwaystation": "europe",
    "beachsunset": "oceania",
    "cityscapes": "generic",
    "landscape": "generic",
    "hikingtrail": "generic",
    "lake": "generic",
    "tourism": "generic",
    "cafe": "generic",
    "restaurant": "generic",
    "airport": "generic",
    "amusementpark": "americas",
    "taxis": "generic",
}


def load_region_overrides() -> dict[str, str]:
    overrides: dict[str, str] = {}
    if not REGIONS_SIDECAR.is_file():
        return overrides
    with REGIONS_SIDECAR.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            content_id = (row.get("contentId") or row.get("content_id") or "").strip()
            region = (row.get("regionTag") or row.get("region_tag") or "").strip().lower()
            if content_id and region:
                overrides[content_id] = region
    return overrides


def resolve_region_tag(category: str, content_id: str, overrides: dict[str, str]) -> str:
    if content_id in overrides:
        return overrides[content_id]
    return CATEGORY_REGION_DEFAULTS.get(category, "generic")


def write_catalog(out_path: Path, overrides: dict[str, str]) -> int:
    if not SOURCE.is_dir():
        raise SystemExit(f"ERROR: sample images directory not found: {SOURCE}")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    rows_written = 0
    with out_path.open("w", encoding="utf-8", newline="") as out_f:
        writer = csv.writer(out_f)
        writer.writerow(
            [
                "category",
                "contentId",
                "author",
                "filePath",
                "imagePath",
                "contentType",
                "regionTag",
            ]
        )
        for csv_path in sorted(SOURCE.glob("*/downloaded_content.csv")):
            category = csv_path.parent.name
            with csv_path.open(encoding="utf-8", newline="") as in_f:
                reader = csv.DictReader(in_f)
                for row in reader:
                    file_path = (row.get("File Path") or "").strip()
                    if not file_path:
                        continue
                    content_id = (row.get("Content ID") or "").strip()
                    region_tag = resolve_region_tag(category, content_id, overrides)
                    writer.writerow(
                        [
                            category,
                            content_id,
                            (row.get("Author") or "").strip(),
                            file_path,
                            f"{PREFIX}/{file_path}",
                            (row.get("Content Type") or "image").strip(),
                            region_tag,
                        ]
                    )
                    rows_written += 1
    return rows_written


def main() -> int:
    overrides = load_region_overrides()
    rows = write_catalog(OUT, overrides)
    rows_test = write_catalog(TEST_OUT, overrides)
    print(f"Wrote {rows} rows to {OUT}")
    print(f"Wrote {rows_test} rows to {TEST_OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
