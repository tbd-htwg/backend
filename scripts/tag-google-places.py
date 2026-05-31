#!/usr/bin/env python3
"""Add seedCategory to google-places.json rows using query/name heuristics."""
from __future__ import annotations

import json
from pathlib import Path

BACKEND = Path(__file__).resolve().parent.parent
PATHS = [
    BACKEND / "tripplanning-seed-job/src/main/resources/seed/google-places.json",
    BACKEND / "tripplanning-seed-job/src/test/resources/seed/google-places.json",
]


def infer_category(place_name: str, formatted_address: str) -> str:
    haystack = f"{place_name} {formatted_address}".lower()
    if any(x in haystack for x in ("hotel", "hostel", "resort", "riad", " lodge", " inn", "suites")):
        return "LODGING"
    if any(x in haystack for x in ("museum", "gallery", "exhibition")):
        return "MUSEUM"
    if any(x in haystack for x in ("café", "cafe", "coffee", "espresso", "bakery")):
        return "CAFE"
    if any(x in haystack for x in ("restaurant", "bistro", "brasserie", "ramen", "dim sum", "gastronomy")):
        return "RESTAURANT"
    if any(x in haystack for x in ("national park", " park", "garden", "trail", "forest", "hiking")):
        return "PARK"
    if any(x in haystack for x in ("viewpoint", "lookout", "observation deck", "summit", " vista")):
        return "VIEWPOINT"
    if any(
        x in haystack
        for x in (
            "tower",
            "monument",
            "cathedral",
            "church",
            "mosque",
            "temple",
            "palace",
            "castle",
            "memorial",
            "falls",
            "bridge",
            "market",
            "square",
            "old town",
            "fort",
            "ruins",
            "island",
            "beach",
            "canyon",
            "reef",
            "volcano",
            "louvre",
            "eiffel",
            "angkor",
            "machu picchu",
            "basilica",
            "abbey",
            "colosseum",
        )
    ):
        return "TOURIST_ATTRACTION"
    return "CITY"


def tag_file(path: Path) -> None:
    places = json.loads(path.read_text(encoding="utf-8"))
    for row in places:
        if row.get("seedCategory"):
            continue
        row["seedCategory"] = infer_category(
            row.get("placeName") or "", row.get("formattedAddress") or ""
        )
    path.write_text(json.dumps(places, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Tagged {len(places)} places in {path}")


def main() -> int:
    for path in PATHS:
        tag_file(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
