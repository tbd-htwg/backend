package com.tripplanning.search;

import java.util.Locale;
import java.util.Optional;

/**
 * Optional scope for aggregate facet suggest: single catalog vs all three.
 */
public enum FacetSuggestScope {
    TRANSPORT,
    LOCATION,
    ACCOMMODATION;

    public static Optional<FacetSuggestScope> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "transport" -> Optional.of(TRANSPORT);
            case "location" -> Optional.of(LOCATION);
            case "accommodation", "accomodation" -> Optional.of(ACCOMMODATION);
            default -> Optional.empty();
        };
    }
}
