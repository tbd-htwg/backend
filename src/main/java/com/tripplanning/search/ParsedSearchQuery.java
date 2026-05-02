package com.tripplanning.search;

import java.util.List;

/**
 * Result of parsing {@code q}: strict {@code key:value} facets and remaining free-text for fuzzy
 * search.
 */
public record ParsedSearchQuery(List<SearchFacet> facets, String freeText) {

    public static ParsedSearchQuery empty() {
        return new ParsedSearchQuery(List.of(), "");
    }

    public boolean hasOnlyBlankFreeText() {
        return freeText == null || freeText.isBlank();
    }
}
