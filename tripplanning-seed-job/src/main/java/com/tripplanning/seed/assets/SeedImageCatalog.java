package com.tripplanning.seed.assets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Region- and category-aware pools for trip stop images. */
public final class SeedImageCatalog {

    private static final Map<PlaceSeedCategory, List<String>> IMAGE_CATEGORIES =
            Map.of(
                    PlaceSeedCategory.CAFE, List.of("cafe", "restaurant"),
                    PlaceSeedCategory.RESTAURANT, List.of("restaurant", "cafe"),
                    PlaceSeedCategory.MUSEUM, List.of("tourism", "castle"),
                    PlaceSeedCategory.TOURIST_ATTRACTION, List.of("tourism", "castle", "cityscapes"),
                    PlaceSeedCategory.PARK, List.of("landscape", "hikingtrail", "lake"),
                    PlaceSeedCategory.VIEWPOINT, List.of("landscape", "cityscapes", "beachsunset"));

    private final Map<String, Map<String, List<String>>> pathsByCategoryAndRegion = new HashMap<>();

    public SeedImageCatalog(List<SampleImageRow> rows) {
        for (SampleImageRow row : rows) {
            String region =
                    row.regionTag() == null || row.regionTag().isBlank()
                            ? PlaceSeedSupport.REGION_GENERIC
                            : row.regionTag().trim().toLowerCase();
            pathsByCategoryAndRegion
                    .computeIfAbsent(row.category(), k -> new HashMap<>())
                    .computeIfAbsent(region, k -> new ArrayList<>())
                    .add(row.imagePath());
        }
    }

    public List<String> pickPaths(
            PlaceSeedCategory stopCategory, String countryCode, int count, Random rng) {
        List<String> categories = IMAGE_CATEGORIES.getOrDefault(stopCategory, List.of("tourism"));
        String region = PlaceSeedSupport.regionBucket(countryCode);
        List<String> pool = resolvePool(categories, region);
        if (pool.isEmpty()) {
            pool = anyPool();
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            picked.add(pool.get(rng.nextInt(pool.size())));
        }
        return picked;
    }

    private List<String> resolvePool(List<String> categories, String region) {
        for (String category : categories) {
            List<String> regional = pool(category, region);
            if (!regional.isEmpty()) {
                return regional;
            }
        }
        for (String category : categories) {
            List<String> generic = pool(category, PlaceSeedSupport.REGION_GENERIC);
            if (!generic.isEmpty()) {
                return generic;
            }
        }
        for (String category : categories) {
            List<String> anyRegion = allForCategory(category);
            if (!anyRegion.isEmpty()) {
                return anyRegion;
            }
        }
        return List.of();
    }

    private List<String> pool(String category, String region) {
        Map<String, List<String>> byRegion = pathsByCategoryAndRegion.get(category);
        if (byRegion == null) {
            return List.of();
        }
        List<String> paths = byRegion.get(region);
        return paths != null ? paths : List.of();
    }

    private List<String> allForCategory(String category) {
        Map<String, List<String>> byRegion = pathsByCategoryAndRegion.get(category);
        if (byRegion == null) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        for (List<String> paths : byRegion.values()) {
            merged.addAll(paths);
        }
        return merged;
    }

    private List<String> anyPool() {
        List<String> merged = new ArrayList<>();
        for (Map<String, List<String>> byRegion : pathsByCategoryAndRegion.values()) {
            for (List<String> paths : byRegion.values()) {
                merged.addAll(paths);
            }
        }
        return merged;
    }
}
