package com.tripplanning.seed.assets;

import java.util.EnumSet;
import java.util.Set;

/** Seed-only place role used when building perf dataset trips. */
public enum PlaceSeedCategory {
    CITY,
    LODGING,
    CAFE,
    RESTAURANT,
    MUSEUM,
    TOURIST_ATTRACTION,
    PARK,
    VIEWPOINT;

    private static final Set<PlaceSeedCategory> POI_STOP_CATEGORIES =
            EnumSet.of(CAFE, RESTAURANT, MUSEUM, TOURIST_ATTRACTION, PARK, VIEWPOINT);

    public boolean isPoiStop() {
        return POI_STOP_CATEGORIES.contains(this);
    }

    public static Set<PlaceSeedCategory> poiStopCategories() {
        return POI_STOP_CATEGORIES;
    }

    public static PlaceSeedCategory fromJson(String value) {
        if (value == null || value.isBlank()) {
            return CITY;
        }
        return PlaceSeedCategory.valueOf(value.trim().toUpperCase());
    }
}
