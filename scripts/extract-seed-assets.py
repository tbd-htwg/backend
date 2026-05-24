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


def main() -> int:
    if not SOURCE.is_dir():
        raise SystemExit(f"ERROR: sample images directory not found: {SOURCE}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    rows_written = 0
    with OUT.open("w", encoding="utf-8", newline="") as out_f:
        writer = csv.writer(out_f)
        writer.writerow(
            ["category", "contentId", "author", "filePath", "imagePath", "contentType"]
        )
        for csv_path in sorted(SOURCE.glob("*/downloaded_content.csv")):
            category = csv_path.parent.name
            with csv_path.open(encoding="utf-8", newline="") as in_f:
                reader = csv.DictReader(in_f)
                for row in reader:
                    file_path = (row.get("File Path") or "").strip()
                    if not file_path:
                        continue
                    writer.writerow(
                        [
                            category,
                            (row.get("Content ID") or "").strip(),
                            (row.get("Author") or "").strip(),
                            file_path,
                            f"{PREFIX}/{file_path}",
                            (row.get("Content Type") or "image").strip(),
                        ]
                    )
                    rows_written += 1

    print(f"Wrote {rows_written} rows to {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
