package com.tripplanning.search;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/** Hibernate Search hits grouped by catalog entity type for strict facet autocomplete. */
@Value
@Builder
public class FacetFilterSuggestResponse {

    List<SearchSuggestionDto> transports;
    List<SearchSuggestionDto> locations;
    List<SearchSuggestionDto> accommodations;

    public static FacetFilterSuggestResponse empty() {
        return FacetFilterSuggestResponse.builder()
                .transports(Collections.emptyList())
                .locations(Collections.emptyList())
                .accommodations(Collections.emptyList())
                .build();
    }
}
