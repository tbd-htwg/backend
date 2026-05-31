package com.tripplanning.seed;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.tripplanning.seed.assets.PlaceSeedCategory;
import com.tripplanning.seed.assets.PlaceSeedSupport;
import com.tripplanning.seed.assets.PrefetchedPlace;

/** Indexes prefetched places by seed category for trip assembly. */
final class PlaceIndex {

    private final List<PrefetchedPlace> all;
    private final Map<PlaceSeedCategory, List<PrefetchedPlace>> byCategory = new EnumMap<>(PlaceSeedCategory.class);
    private final Map<String, List<PrefetchedPlace>> byCountry = new HashMap<>();

    PlaceIndex(List<PrefetchedPlace> places) {
        this.all = List.copyOf(places);
        for (PlaceSeedCategory category : PlaceSeedCategory.values()) {
            byCategory.put(category, new ArrayList<>());
        }
        for (PrefetchedPlace place : places) {
            PlaceSeedCategory category = PlaceSeedSupport.resolveCategory(place);
            byCategory.get(category).add(place);
            byCountry.computeIfAbsent(place.countryCode(), k -> new ArrayList<>()).add(place);
        }
    }

    List<PrefetchedPlace> all() {
        return all;
    }

    List<PrefetchedPlace> cities() {
        return byCategory.get(PlaceSeedCategory.CITY);
    }

    List<PrefetchedPlace> lodging() {
        return byCategory.get(PlaceSeedCategory.LODGING);
    }

    List<PrefetchedPlace> poiStops() {
        List<PrefetchedPlace> poi = new ArrayList<>();
        for (PlaceSeedCategory category : PlaceSeedCategory.poiStopCategories()) {
            poi.addAll(byCategory.get(category));
        }
        return poi;
    }

    PrefetchedPlace pickDestination(Random rng) {
        List<PrefetchedPlace> cities = cities();
        if (!cities.isEmpty()) {
            return cities.get(rng.nextInt(cities.size()));
        }
        return all.get(rng.nextInt(all.size()));
    }

    PrefetchedPlace pickDestinationForTopic(Set<String> countryCodes, Random rng) {
        List<PrefetchedPlace> cities = cities();
        if (!countryCodes.isEmpty() && !cities.isEmpty()) {
            List<PrefetchedPlace> matched = new ArrayList<>();
            for (PrefetchedPlace city : cities) {
                if (countryCodes.contains(city.countryCode().toUpperCase(Locale.ROOT))) {
                    matched.add(city);
                }
            }
            if (!matched.isEmpty()) {
                return matched.get(rng.nextInt(matched.size()));
            }
        }
        return pickDestination(rng);
    }

    PrefetchedPlace pickFromPool(List<PrefetchedPlace> pool, Random rng) {
        if (pool.isEmpty()) {
            return all.get(rng.nextInt(all.size()));
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    List<PrefetchedPlace> intersectPool(List<PrefetchedPlace> tripPool, List<PrefetchedPlace> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<PrefetchedPlace> out = new ArrayList<>();
        for (PrefetchedPlace candidate : candidates) {
            for (PrefetchedPlace near : tripPool) {
                if (near.googlePlaceId().equals(candidate.googlePlaceId())) {
                    out.add(candidate);
                    break;
                }
            }
        }
        return out;
    }

    List<PrefetchedPlace> poiInPool(List<PrefetchedPlace> tripPool) {
        return intersectPool(tripPool, poiStops());
    }

    List<PrefetchedPlace> lodgingInPool(List<PrefetchedPlace> tripPool) {
        List<PrefetchedPlace> lodging = lodging();
        List<PrefetchedPlace> inPool = intersectPool(tripPool, lodging);
        if (!inPool.isEmpty()) {
            return inPool;
        }
        return lodging;
    }

    List<PrefetchedPlace> poiNear(PrefetchedPlace anchor, Random rng) {
        List<PrefetchedPlace> sameCountry = byCountry.getOrDefault(anchor.countryCode(), List.of());
        List<PrefetchedPlace> local = new ArrayList<>();
        for (PrefetchedPlace place : poiStops()) {
            if (sameCountry.stream().anyMatch(p -> p.googlePlaceId().equals(place.googlePlaceId()))) {
                local.add(place);
            }
        }
        if (!local.isEmpty()) {
            return local;
        }
        return poiStops();
    }
}
