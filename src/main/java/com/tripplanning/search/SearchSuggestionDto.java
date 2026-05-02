package com.tripplanning.search;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SearchSuggestionDto {
    long id;
    String label;
    String secondary;
}
