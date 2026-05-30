package com.tripplanning.seed.assets;

import java.util.Locale;
import java.util.Set;

/** Resolves seed categories and region buckets for places and images. */
public final class PlaceSeedSupport {

    public static final String REGION_GENERIC = "generic";

    private static final Set<String> EUROPE =
            Set.of(
                    "AD", "AL", "AM", "AT", "AZ", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DE", "DK", "EE",
                    "ES", "FI", "FR", "GB", "GE", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV",
                    "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI", "SK",
                    "SM", "TR", "UA", "VA", "XK");
    private static final Set<String> ASIA =
            Set.of(
                    "BD", "BN", "BT", "CN", "HK", "ID", "IN", "IR", "JP", "KH", "KR", "KZ", "LA", "LK", "MM",
                    "MN", "MO", "MY", "NP", "PH", "PK", "SG", "TH", "TW", "UZ", "VN");
    private static final Set<String> AMERICAS =
            Set.of(
                    "AR", "BO", "BR", "BS", "BZ", "CA", "CL", "CO", "CR", "CU", "DO", "EC", "GT", "HN", "JM",
                    "MX", "NI", "PA", "PE", "PR", "PY", "US", "UY", "VE");
    private static final Set<String> OCEANIA = Set.of("AU", "FJ", "NC", "NZ", "PF", "PG", "RE", "SC", "TO", "VU");
    private static final Set<String> AFRICA =
            Set.of(
                    "AO", "BF", "BI", "BW", "CD", "CI", "CM", "DZ", "EG", "ET", "GA", "GH", "KE", "MA", "MG",
                    "MU", "MW", "MZ", "NA", "NG", "RW", "SN", "TZ", "TN", "UG", "ZA", "ZM", "ZW");
    private static final Set<String> MIDDLE_EAST =
            Set.of("AE", "BH", "IL", "IQ", "JO", "KW", "LB", "OM", "QA", "SA", "SY", "YE");

    private PlaceSeedSupport() {}

    public static PlaceSeedCategory resolveCategory(PrefetchedPlace place) {
        if (place.seedCategory() != null && !place.seedCategory().isBlank()) {
            return PlaceSeedCategory.fromJson(place.seedCategory());
        }
        return inferCategory(place.placeName(), place.formattedAddress());
    }

    static PlaceSeedCategory inferCategory(String placeName, String formattedAddress) {
        String haystack =
                ((placeName != null ? placeName : "") + " " + (formattedAddress != null ? formattedAddress : ""))
                        .toLowerCase(Locale.ROOT);
        if (containsAny(haystack, "hotel", "hostel", "resort", "riad", " lodge", " inn", "suites", "accommodation")) {
            return PlaceSeedCategory.LODGING;
        }
        if (containsAny(haystack, "museum", "gallery", "exhibition")) {
            return PlaceSeedCategory.MUSEUM;
        }
        if (containsAny(haystack, "café", "cafe", "coffee", "espresso", "bakery", "patisserie")) {
            return PlaceSeedCategory.CAFE;
        }
        if (containsAny(
                haystack,
                "restaurant",
                "bistro",
                "brasserie",
                "ramen",
                "dim sum",
                "gastronomy",
                "taverna",
                "trattoria",
                "izakaya")) {
            return PlaceSeedCategory.RESTAURANT;
        }
        if (containsAny(
                haystack,
                "national park",
                " park",
                "garden",
                "trail",
                "forest",
                "lake circuit",
                "hiking")) {
            return PlaceSeedCategory.PARK;
        }
        if (containsAny(haystack, "viewpoint", "lookout", "observation deck", "summit", " vista")) {
            return PlaceSeedCategory.VIEWPOINT;
        }
        if (containsAny(
                haystack,
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
                "old city",
                "fort",
                "ruins",
                "island",
                "beach",
                "canyon",
                "reef",
                "volcano",
                "basilica",
                "abbey",
                "colosseum",
                "louvre",
                "eiffel",
                "angkor",
                "machu picchu")) {
            return PlaceSeedCategory.TOURIST_ATTRACTION;
        }
        return PlaceSeedCategory.CITY;
    }

    public static String regionBucket(String countryCode) {
        if (countryCode == null || countryCode.isBlank() || "XX".equalsIgnoreCase(countryCode)) {
            return REGION_GENERIC;
        }
        String cc = countryCode.toUpperCase(Locale.ROOT);
        if (EUROPE.contains(cc)) {
            return "europe";
        }
        if (ASIA.contains(cc)) {
            return "asia";
        }
        if (AMERICAS.contains(cc)) {
            return "americas";
        }
        if (OCEANIA.contains(cc)) {
            return "oceania";
        }
        if (AFRICA.contains(cc)) {
            return "africa";
        }
        if (MIDDLE_EAST.contains(cc)) {
            return "middle_east";
        }
        return REGION_GENERIC;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
