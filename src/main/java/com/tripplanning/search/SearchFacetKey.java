package com.tripplanning.search;

import java.util.Locale;
import java.util.Optional;

/**
 * Facet keys in search queries and their Hibernate Search field paths on {@link com.tripplanning.trip.TripEntity}
 * documents.
 */
public enum SearchFacetKey {
    TRANSPORT("transports.type_keyword"),
    LOCATION("tripLocations.location.destination_keyword"),
    ACCOMMODATION("accommodations.type_keyword"),
    DESTINATION("destination_keyword"),
    AUTHOR("user.name_keyword");

    private final String tripIndexFieldPath;

    SearchFacetKey(String tripIndexFieldPath) {
        this.tripIndexFieldPath = tripIndexFieldPath;
    }

    public String tripIndexFieldPath() {
        return tripIndexFieldPath;
    }

    /**
     * Parses the key segment before {@code :} in a facet token (case-insensitive). Maps typo
     * {@code accomodation} to accommodation; {@code user} and {@code author} to author facet.
     */
    public static Optional<SearchFacetKey> fromQueryKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return Optional.empty();
        }
        String k = rawKey.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "transport" -> Optional.of(TRANSPORT);
            case "location" -> Optional.of(LOCATION);
            case "accommodation", "accomodation" -> Optional.of(ACCOMMODATION);
            case "destination" -> Optional.of(DESTINATION);
            case "user", "author" -> Optional.of(AUTHOR);
            default -> Optional.empty();
        };
    }
}
