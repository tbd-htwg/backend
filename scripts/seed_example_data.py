#!/usr/bin/env python3
"""Seed example data for the Trip Planning backend via REST API."""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass
from datetime import date, timedelta, datetime
from typing import Dict, List, Tuple
from urllib import error, request


ROOT_URL = os.getenv("ROOT_URL", "http://localhost:8080")
BASE_PATH = os.getenv("BASE_PATH", "")
API_BASE = f"{ROOT_URL.rstrip('/')}/{BASE_PATH.strip('/')}" if BASE_PATH else ROOT_URL.rstrip("/")
TIMEOUT_SECONDS = 20


@dataclass(frozen=True)
class EntityRef:
    id: int
    uri: str


class ApiError(RuntimeError):
    pass


class ApiClient:
    def __init__(self, api_base: str) -> None:
        self.api_base = api_base.rstrip("/")

    def _url(self, path: str) -> str:
        return f"{self.api_base}/{path.lstrip('/')}"

    def _request(
        self,
        method: str,
        path: str,
        body: str | None = None,
        content_type: str = "application/json",
        expected_statuses: Tuple[int, ...] = (200,),
    ) -> Tuple[int, dict, str]:
        headers = {"Accept": "application/hal+json"}
        if body is not None:
            headers["Content-Type"] = content_type

        req = request.Request(
            self._url(path),
            data=body.encode("utf-8") if body is not None else None,
            headers=headers,
            method=method,
        )

        try:
            with request.urlopen(req, timeout=TIMEOUT_SECONDS) as response:
                status = response.getcode()
                response_headers = dict(response.getheaders())
                response_body = response.read().decode("utf-8")
        except error.HTTPError as exc:
            message = exc.read().decode("utf-8", errors="replace")
            raise ApiError(f"{method} {path} failed with {exc.code}: {message}") from exc
        except error.URLError as exc:
            raise ApiError(f"{method} {path} failed: {exc.reason}") from exc

        if status not in expected_statuses:
            raise ApiError(f"{method} {path} returned {status}, expected {expected_statuses}. Body: {response_body}")

        return status, response_headers, response_body

    def create_entity(self, path: str, payload: dict) -> EntityRef:
        _, headers, _ = self._request(
            method="POST",
            path=path,
            body=json.dumps(payload),
            content_type="application/json",
            expected_statuses=(201,),
        )
        location = headers.get("Location") or headers.get("location")
        if not location:
            raise ApiError(f"POST {path} succeeded but did not return a Location header.")

        entity_id = extract_id(location)
        return EntityRef(id=entity_id, uri=location)

    def write_uri_list(self, method: str, path: str, uris: List[str]) -> None:
        body = "\n".join(uris)
        self._request(
            method=method,
            path=path,
            body=body,
            content_type="text/uri-list",
            expected_statuses=(200, 201, 204),
        )


def extract_id(location: str) -> int:
    match = re.search(r"/(\d+)(?:/)?$", location.strip())
    if not match:
        raise ApiError(f"Could not parse numeric id from Location header: {location}")
    return int(match.group(1))


def make_start_date(index: int) -> str:
    return (date.today() + timedelta(days=7 + index * 5)).isoformat()


def main() -> int:
    print(f"Seeding API at {API_BASE}")
    client = ApiClient(API_BASE)
    now_suffix = datetime.utcnow().strftime("%Y%m%d%H%M%S")

    users_by_key: Dict[str, EntityRef] = {}
    trips_by_key: Dict[str, Dict[str, int | str]] = {}
    locations_by_key: Dict[str, EntityRef] = {}
    accommodations_by_key: Dict[str, EntityRef] = {}
    transports_by_key: Dict[str, EntityRef] = {}

    user_templates = [
        ("anna", "Anna", "Mountain lover and coffee fan."),
        ("ben", "Ben", "Weekend backpacker and city explorer."),
        ("carla", "Carla", "Food-focused traveler with camera."),
        ("dario", "Dario", "Hiking guide and train enthusiast."),
        ("elena", "Elena", "Slow travel fan and remote worker."),
    ]

    print("Creating users...")
    for idx, (slug, display_name, description) in enumerate(user_templates, start=1):
        key = f"user{idx}"
        payload = {
            "email": f"{slug}.{now_suffix}@example.com",
            "name": display_name,
            "imageUrl": f"https://picsum.photos/seed/{slug}-{now_suffix}/300/300",
            "description": description,
        }
        users_by_key[key] = client.create_entity("/users", payload)

    trip_topics = [
        ("City Break", "Architecture and food tour"),
        ("Nature Escape", "Lakes, trails, and sunrise views"),
        ("Culture Weekend", "Museums, markets, and live music"),
    ]

    print("Creating trips (3 per user)...")
    trip_counter = 0
    for user_idx, user_key in enumerate(users_by_key.keys(), start=1):
        user_ref = users_by_key[user_key]
        for local_trip_idx, (topic, short_text) in enumerate(trip_topics, start=1):
            trip_counter += 1
            key = f"{user_key}_trip{local_trip_idx}"
            payload = {
                "user": user_ref.uri,
                "title": f"{topic} #{trip_counter}",
                "destination": f"Destination {trip_counter}",
                "startDate": make_start_date(trip_counter),
                "shortDescription": short_text,
                "longDescription": (
                    f"Generated example trip {trip_counter}. "
                    "This plan includes practical transport and accommodation options."
                ),
            }
            trip_ref = client.create_entity("/trips", payload)
            trips_by_key[key] = {"id": trip_ref.id, "uri": trip_ref.uri, "owner_user_id": user_ref.id}

    location_names = [
        "Zurich Central",
        "Lucerne Old Town",
        "Interlaken",
        "Geneva Lakefront",
        "Bern Historic Center",
        "Basel Riverside",
        "Lugano Promenade",
        "Chur Station",
        "St. Gallen Abbey",
        "Lausanne Harbor",
        "Zermatt Village",
        "Arosa Panorama Point",
    ]

    print("Creating locations...")
    for idx, name in enumerate(location_names, start=1):
        locations_by_key[f"location{idx}"] = client.create_entity("/locations", {"name": name})

    print("Creating trip-locations...")
    location_values = list(locations_by_key.values())
    for trip_idx, (trip_key, trip_data) in enumerate(trips_by_key.items()):
        trip_uri = str(trip_data["uri"])
        for stop_offset in (0, 1):
            location_ref = location_values[(trip_idx + stop_offset) % len(location_values)]
            payload = {
                "trip": trip_uri,
                "location": location_ref.uri,
                "imageUrl": f"https://picsum.photos/seed/tripstop-{trip_idx}-{stop_offset}/1024/576",
                "description": f"Stop {stop_offset + 1} for {trip_key}.",
            }
            client.create_entity("/trip-locations", payload)

    accommodation_templates = [
        ("Hotel", "Alpine Grand Hotel", "Bahnhofstrasse 10"),
        ("Hostel", "Backpack Base", "Riverside 3"),
        ("Apartment", "City Loft Suites", "Market Alley 5"),
        ("Guesthouse", "Sunrise Guesthouse", "Hill Road 18"),
        ("Resort", "Lakeview Resort", "Harbor Street 42"),
        ("Cabin", "Forest Edge Cabins", "Pine Trail 7"),
    ]

    transport_types = ["Train", "Bus", "Plane", "Ferry", "Bicycle", "Car Rental"]

    print("Creating accommodations and transports...")
    for idx, (acc_type, name, address) in enumerate(accommodation_templates, start=1):
        accommodations_by_key[f"acc{idx}"] = client.create_entity(
            "/accommodations",
            {"type": acc_type, "name": name, "address": address},
        )

    for idx, transport_type in enumerate(transport_types, start=1):
        transports_by_key[f"transport{idx}"] = client.create_entity("/transports", {"type": transport_type})

    print("Linking accommodations and transports to trips...")
    acc_values = list(accommodations_by_key.values())
    transport_values = list(transports_by_key.values())
    for trip_idx, trip_data in enumerate(trips_by_key.values()):
        trip_id = int(trip_data["id"])
        acc_links = [
            acc_values[trip_idx % len(acc_values)].uri,
            acc_values[(trip_idx + 1) % len(acc_values)].uri,
        ]
        transport_links = [
            transport_values[trip_idx % len(transport_values)].uri,
            transport_values[(trip_idx + 2) % len(transport_values)].uri,
        ]
        client.write_uri_list("PUT", f"/trips/{trip_id}/accommodations", acc_links)
        client.write_uri_list("PUT", f"/trips/{trip_id}/transports", transport_links)

    print("Creating likes...")
    user_values = list(users_by_key.values())
    for trip_idx, trip_data in enumerate(trips_by_key.values()):
        trip_id = int(trip_data["id"])
        owner_user_id = int(trip_data["owner_user_id"])
        like_candidates = [
            user_values[(trip_idx + 1) % len(user_values)],
            user_values[(trip_idx + 2) % len(user_values)],
        ]
        like_uris = [u.uri for u in like_candidates if u.id != owner_user_id]
        if like_uris:
            client.write_uri_list("PATCH", f"/trips/{trip_id}/likedByUsers", like_uris)

    print("Creating comments...")
    for trip_idx, trip_data in enumerate(trips_by_key.values()):
        trip_uri = str(trip_data["uri"])
        commenter_a = user_values[(trip_idx + 1) % len(user_values)]
        commenter_b = user_values[(trip_idx + 3) % len(user_values)]
        comments = [
            f"Looks great! I would do this route too. (trip {trip_idx + 1})",
            f"Nice plan. The transport and stay choices look very practical. (trip {trip_idx + 1})",
        ]
        client.create_entity("/comments", {"content": comments[0], "user": commenter_a.uri, "trip": trip_uri})
        client.create_entity("/comments", {"content": comments[1], "user": commenter_b.uri, "trip": trip_uri})

    print("Seed complete.")
    print(
        "Created counts: "
        f"users={len(users_by_key)}, trips={len(trips_by_key)}, locations={len(locations_by_key)}, "
        f"accommodations={len(accommodations_by_key)}, transports={len(transports_by_key)}, "
        f"tripLocations={len(trips_by_key) * 2}, likes~={len(trips_by_key) * 2}, comments={len(trips_by_key) * 2}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ApiError as exc:
        print(f"Seed failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
